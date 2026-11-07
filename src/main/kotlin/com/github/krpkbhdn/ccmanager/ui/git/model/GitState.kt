package com.github.krpkbhdn.ccmanager.ui.git.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitMisc
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private const val UNEXPECTED_ERROR = "Something went wrong"
private const val LOAD_FAILED = "Could not read the repository"
internal const val GIT_BUSY = "Git is busy"

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
    /** Shows [directory], a worktree of [projectId], and optionally starts a session there under that project. */
    val showWorktree: (projectId: Long, directory: Path, launchSession: Boolean) -> Unit = { _, _, _ -> },
    /** In-memory part of forgetting a removed worktree; the stored part is [forgetStoredWorktree]. */
    val forgetWorktree: (directory: Path) -> Unit = {},
    val stopWorktreeWork: (directory: Path) -> Unit = {},
    val hasRunningWork: (directory: Path) -> Boolean = { false },
    /** True while processes of closed sessions and shells are still being ended, so their folders may still be locked. */
    val workStopping: () -> Boolean = { false },
    val openDirectory: (directory: Path) -> Unit = {},
    /** These three touch the database: call them off the UI thread. */
    val deleteRunConfigurations: (directory: Path) -> Unit = {},
    val runConfigurationNames: (directory: Path) -> List<String> = { emptyList() },
    val forgetStoredWorktree: (directory: Path) -> Unit = {},
    /** Waits [millis] before the retry of a removal Windows still holds; tests replace it so they do not sleep. */
    val unlockPause: (millis: Long) -> Unit = Thread::sleep,
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
    /** The task row when this state is for a task worktree: its base branch and task text; null for the main folder and other worktrees. */
    task: TaskWorktree? = null,
) {
    /** Filled in after the state exists when the task row is read off the UI thread. */
    var task by mutableStateOf(task)
        internal set

    /** Whether the worktree differs from its task base; null while unknown (not a task worktree, or not checked yet). */
    var baseChanges by mutableStateOf<Boolean?>(null)
        internal set

    /** The branch this one was merged into locally during this run; set only by the merge flow. */
    var mergedInto by mutableStateOf<String?>(null)
        internal set

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
    var ghStatus by mutableStateOf<GhAvailability?>(null)
        internal set
    var pullRequest by mutableStateOf<PullRequest?>(null)
        internal set
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
    var contextPopupOpen by mutableStateOf(false)
    var commitFocusTick by mutableIntStateOf(0)
        private set

    val draft = CommitDraft()
    val review = ReviewComments()
    val log = LogModel({ repo }, git, scope) { fail("Could not read history", it) }

    val view: GitSubView get() = host.prefs().viewOf(projectId)
    val pullMode: GitMisc.PullMode get() = host.prefs().pullMode
    val flat: Boolean get() = host.prefs().flatChanges
    val busy: Boolean get() = job != null
    val summary: GitSummary? get() = status?.let(::summaryOf)
    val hasGithubRemote: Boolean get() = remotes.any { isGithubRemote(it.pushUrl() ?: it.fetchUrl()) }

    /** Why publishing or merging has nothing to do, or null when it has something (or that is not known). */
    val noChangesHint: String? get() = task?.baseBranch()?.takeIf { baseChanges == false }?.let { "No changes against $it" }

    private val jobLock = Any()
    private val refresher = Refresher(scope) { reload() }
    private val reloadLock = Mutex()
    private val pendingProgress = AtomicReference<String?>()
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
        folderVanishes: Boolean = false,
        onSuccess: (T) -> Unit = {},
        call: GitBackend.((String) -> Unit) -> T,
    ) {
        val running = synchronized(jobLock) { job ?: run { job = GitJob(label); jobCount++; null } }
        if (running != null) return host.notify(NotificationKind.INFO, GIT_BUSY, "Wait for ${running.label.lowercase()} to finish")
        scope.launch {
            val outcome = attempt(label, handle, { act(label, handle, refreshWorktrees, folderVanishes, onSuccess, call) }, call)
            synchronized(jobLock) { job = null }
            if (outcome == null || !folderVanishes) reloadAfterAct(refreshWorktrees)
            outcome?.let {
                error = null
                onSuccess(it.value)
            }
        }
    }

    private suspend fun reloadAfterAct(refreshWorktrees: Boolean) {
        refresh()
        loadAuxiliary()
        if (refreshWorktrees) loadWorktrees()
    }

    private class Success<T>(val value: T)

    private suspend fun <T> attempt(label: String, handle: (GitException) -> Boolean, again: () -> Unit, call: GitBackend.((String) -> Unit) -> T): Success<T>? =
        synchronized(jobLock) { jobCount }.let { thisJob ->
            gitCall({ failure -> if (!handle(failure)) fail(label, failure, again) }) {
                Success(git.call { line -> postProgress(thisJob, line) })
            }
        }

    /** Chatty git output costs one pending update at most: later lines replace the one not shown yet. */
    private fun postProgress(thisJob: Int, line: String) {
        if (pendingProgress.getAndSet(line) != null) return
        scope.launch {
            val latest = pendingProgress.getAndSet(null)
            synchronized(jobLock) { if (thisJob == jobCount) job = job?.copy(progress = latest) }
        }
    }

    internal fun fail(label: String, e: GitException, again: (() -> Unit)? = null) {
        val plan = errorPlan(e, label)
        error = plan
        retry = again
        host.notify(NotificationKind.ERROR, label, plan.headline)
    }

    internal fun success(title: String, body: String = "") = host.notify(NotificationKind.FINISHED, title, body)

    internal fun retryLast() = retry?.invoke() ?: refresh()

    private suspend fun reload() = reloadLock.withLock {
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
        if (current.branch() != status?.branch()) {
            pullRequest = null
            mergedInto = null
        }
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
