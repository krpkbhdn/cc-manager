package com.github.krpkbhdn.ccmanager.ui.git.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitMisc
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNEXPECTED_ERROR = "Something went wrong"
private const val LOAD_FAILED = "Could not read the repository"
private const val GIT_BUSY = "Git is busy"

/** Wraps anything that is not already a [GitException] so the UI has one failure type to show. */
internal fun asGitException(e: RuntimeException): GitException =
    e as? GitException ?: GitException(GitException.Kind.OTHER, e.message ?: UNEXPECTED_ERROR, "")

/** Runs a blocking git [block] off the UI thread; a failure goes to [onError] and the result is null. */
internal suspend fun <T> gitCall(onError: (GitException) -> Unit, block: () -> T): T? =
    try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        onError(asGitException(e))
        null
    }

/** What [GitState] needs from the app around it: remembered preferences and a way to tell the user things. */
class GitHost(
    val prefs: () -> GitPrefs,
    val updatePrefs: ((GitPrefs) -> GitPrefs) -> Unit,
    val notify: (NotificationKind, String, String) -> Unit,
    val openWorktree: (directory: Path, launchSession: Boolean) -> Unit = { _, _ -> },
    val forgetWorktree: (directory: Path) -> Unit = {},
    val registerWorktree: (directory: Path) -> Unit = {},
    val stopWorktreeWork: (directory: Path) -> Unit = {},
    val hasRunningWork: (directory: Path) -> Boolean = { false },
    val openDirectory: (directory: Path) -> Unit = {},
)

/** The running mutation: what it does and the latest progress line git printed. */
data class GitJob(val label: String, val progress: String? = null)

/** A file picked in the Changes view; [staged] says which of its two diffs is shown. */
data class ChangeRef(val path: String, val staged: Boolean)

/**
 * Live state of one project's repository for the Git tab: status and branches reload coalesced on watcher events and
 * after every mutation, mutations run one at a time off the UI thread, failures become an [ErrorPlan]. Writes on [scope].
 */
class GitState(
    val projectId: Long,
    private val folder: Path,
    internal val git: GitBackend,
    internal val scope: CoroutineScope,
    internal val host: GitHost,
) {
    /** The repository top level, re-read on every reload: git reports paths relative to it, whatever subfolder the project is. */
    @Volatile
    var repo: Path = folder
        private set

    var status by mutableStateOf<GitStatus?>(null)
        private set
    var branches by mutableStateOf<List<GitBranch>>(emptyList())
        private set
    var remotes by mutableStateOf<List<GitMisc.Remote>>(emptyList())
        private set
    var identity by mutableStateOf<GitMisc.Identity?>(null)
        private set
    var stashes by mutableStateOf<List<GitMisc.Stash>>(emptyList())
        private set
    var tags by mutableStateOf<List<GitMisc.Tag>>(emptyList())
        private set
    var worktrees by mutableStateOf<List<Worktree>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<ErrorPlan?>(null)
        private set
    var job by mutableStateOf<GitJob?>(null)
        private set
    var error by mutableStateOf<ErrorPlan?>(null)
    var dialog by mutableStateOf<GitDialog?>(null)
    var selected by mutableStateOf<ChangeRef?>(null)
    var branchPopupOpen by mutableStateOf(false)
    var commitFocusTick by mutableIntStateOf(0)
        private set

    val draft = CommitDraft()
    val log = LogModel({ repo }, git, scope) { fail("Could not read history", it) }

    val view: GitSubView get() = host.prefs().viewOf(projectId)
    val pullMode: GitMisc.PullMode get() = host.prefs().pullMode
    val flat: Boolean get() = host.prefs().flatChanges
    val busy: Boolean get() = job != null
    val summary: GitSummary? get() = status?.let(::summaryOf)

    private val refresher = Refresher(scope) { reload() }
    private var retry: (() -> Unit)? = null
    private var jobCount = 0
    private var refsSignature = ""
    private var worktreesLoaded = false
    private var commitFocusHandled = 0

    fun show(view: GitSubView) {
        host.updatePrefs { it.withView(projectId, view) }
        if (view == GitSubView.WORKTREES && !worktreesLoaded) scope.launch { loadWorktrees() }
    }

    fun requestCommitFocus() {
        show(GitSubView.CHANGES)
        commitFocusTick++
    }

    /** True once per [requestCommitFocus], so re-opening the Changes view later does not steal the focus again. */
    fun takeCommitFocusRequest(): Boolean = (commitFocusTick > commitFocusHandled).also { commitFocusHandled = commitFocusTick }

    fun setPullMode(mode: GitMisc.PullMode) = host.updatePrefs { it.copy(pullMode = mode) }

    fun setFlatChanges(flat: Boolean) = host.updatePrefs { it.copy(flatChanges = flat) }

    fun refresh() = refresher.request()

    /** Reloads now and returns when done, for callers that load many projects one after another. */
    suspend fun reloadNow() = reload()

    /** The toolbar Refresh: also re-reads tags, remotes and stashes, which a terminal can change without the watcher noticing. */
    fun refreshAll() {
        refresh()
        scope.launch { loadAuxiliary() }
    }

    /** Re-reads the worktrees, apart from the other lists: each one costs a status and a merge check. */
    suspend fun loadWorktrees() {
        if (status?.repository() != true) return
        worktrees = gitCall({ fail("Could not read worktrees", it) }) { git.worktrees.list(repo) } ?: return
        worktreesLoaded = true
    }

    /** Stops the reload loop once the project is gone. */
    fun close() = refresher.close()

    /** Subscribes to repository changes until the returned handle runs; a missing watcher only costs live updates. */
    fun attach(): Runnable {
        val subscription = scope.async(Dispatchers.IO) {
            try {
                git.watcher.subscribe(folder) { refresh() }
            } catch (e: RuntimeException) {
                Runnable { }
            }
        }
        refresh()
        return Runnable { scope.launch(Dispatchers.IO) { subscription.await().run() } }
    }

    /** Diff of one file for the pane; failures show as a toast and yield null. */
    suspend fun diffOf(ref: ChangeRef) = guarded("Could not read the diff") { git.status.diff(repo, ref.path, ref.staged) }

    suspend fun <T> guarded(label: String, call: GitBackend.() -> T): T? = gitCall({ fail(label, it) }) { git.call() }

    /** Runs one mutation at a time; progress lines flow into [job], then state reloads. [handle] may take over a failure. */
    internal fun <T> act(
        label: String,
        handle: (GitException) -> Boolean = { false },
        refreshWorktrees: Boolean = false,
        onSuccess: (T) -> Unit = {},
        call: GitBackend.((String) -> Unit) -> T,
    ) {
        job?.let { return host.notify(NotificationKind.INFO, GIT_BUSY, "Wait for ${it.label.lowercase()} to finish") }
        job = GitJob(label)
        jobCount++
        scope.launch {
            val outcome = attempt(label, handle, { act(label, handle, refreshWorktrees, onSuccess, call) }, call)
            job = null
            refresh()
            loadAuxiliary()
            if (refreshWorktrees) loadWorktrees()
            outcome?.let {
                error = null
                onSuccess(it.value)
            }
        }
    }

    private class Success<T>(val value: T)

    private suspend fun <T> attempt(label: String, handle: (GitException) -> Boolean, again: () -> Unit, call: GitBackend.((String) -> Unit) -> T): Success<T>? =
        jobCount.let { thisJob ->
            gitCall({ failure -> if (!handle(failure)) fail(label, failure, again) }) {
                Success(git.call { line -> scope.launch { if (thisJob == jobCount) job = job?.copy(progress = line) } })
            }
        }

    internal fun fail(label: String, e: GitException, again: (() -> Unit)? = null) {
        val plan = errorPlan(e, label)
        error = plan
        retry = again
        host.notify(NotificationKind.ERROR, label, plan.headline)
    }

    internal fun success(title: String, body: String = "") = host.notify(NotificationKind.FINISHED, title, body)

    internal fun retryLast() = retry?.invoke()

    private suspend fun reload() {
        loading = true
        try {
            val loaded = gitCall(::failLoad) {
                repo = git.status.topLevel(folder)
                val current = git.status.status(repo)
                current to if (current.repository()) git.branches.branches(repo) else emptyList()
            }
            if (loaded != null) {
                applyLoaded(loaded.first, loaded.second)
                loadError = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            failLoad(asGitException(e))
        } finally {
            loading = false
        }
    }

    private suspend fun applyLoaded(current: GitStatus, currentBranches: List<GitBranch>) {
        val firstLoad = status == null
        status = current
        branches = currentBranches
        selected = selected?.takeIf { ref -> current.changes().any { it.path() == ref.path && (if (ref.staged) it.hasStaged() else it.hasUnstaged()) } }
        val signature = current.head().orEmpty() + currentBranches.joinToString { it.name() + it.commit() }
        if (signature != refsSignature && log.loaded) log.reload()
        refsSignature = signature
        if (current.repository() && view == GitSubView.WORKTREES) loadWorktrees()
        if (current.repository() && (firstLoad || current.stashCount() != stashes.size || current.stashTop() != stashes.firstOrNull()?.hash())) loadAuxiliary()
    }

    private class Auxiliary(val remotes: List<GitMisc.Remote>, val identity: GitMisc.Identity?, val stashes: List<GitMisc.Stash>, val tags: List<GitMisc.Tag>)

    private suspend fun loadAuxiliary() {
        if (status?.repository() != true) return
        val aux = gitCall(::failLoad) {
            Auxiliary(git.status.remotes(repo), git.status.identity(repo), git.stashes.list(repo), git.history.tags(repo))
        } ?: return
        remotes = aux.remotes
        identity = aux.identity
        stashes = aux.stashes
        tags = aux.tags
    }

    private fun failLoad(e: GitException) {
        loadError = errorPlan(e, LOAD_FAILED)
    }
}
