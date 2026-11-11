package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.git.FolderIdentity
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.git.model.GitBackend
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import com.github.krpkbhdn.ccmanager.ui.git.model.sameFolder
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ProjectGits::class.java)

/**
 * The per-project [GitState]s, created on first use and kept in step with the registered projects. A project has its main
 * folder and, lazily, one state per worktree switched to; [of] answers the active one, [mainOf] always the main folder.
 */
class ProjectGits(
    private val git: GitBackend,
    private val scope: CoroutineScope,
    private val host: GitHost,
    private val projectOf: (Long) -> Project?,
    private val tasksOf: (Long) -> List<TaskWorktree> = { emptyList() },
    private val sessionDirectories: () -> Map<String, Path> = { emptyMap() },
) {
    private val states = mutableStateMapOf<Long, GitState>()
    private val worktreeStates = mutableMapOf<Path, GitState>()
    private val tasksLoaded = mutableSetOf<Path>()
    private val sweep = Mutex()

    /** The worktree each project shows, in memory only; a project missing here shows its main folder. */
    private val active = mutableStateMapOf<Long, Path>()

    /** Raised whenever a task row is created or forgotten, so views of the rows can re-read them. */
    var taskVersion by mutableIntStateOf(0)
        private set

    /** Git state of the project's active folder, the main one unless a worktree was switched to; null for an unknown project or a group. */
    fun of(projectId: Long): GitState? = active[projectId]?.let(worktreeStates::get) ?: mainOf(projectId)

    /** Git state of the project's own folder, whatever worktree is active; null for an unknown project or a group (its directory is no repository). */
    fun mainOf(projectId: Long): GitState? {
        states[projectId]?.let { return it }
        val project = projectOf(projectId)?.takeIf { !it.group } ?: return null
        return GitState(projectId, project.path, git, scope, host).also { states[projectId] = it }
    }

    /** The worktree the project shows; null when it shows its main folder. */
    fun activeDirectoryOf(projectId: Long): Path? = active[projectId]

    /**
     * Shows [directory], a worktree of the project, or its main folder for null or the project's own folder. The folder is
     * checked against the project's worktree list in the background; a folder that is not one of them is dropped again.
     */
    fun switchTo(projectId: Long, directory: Path?) {
        val main = mainOf(projectId) ?: return
        val folder = directory?.let(FolderIdentity::of)
        if (folder == null || sameFolder(folder, main.repo)) {
            active.remove(projectId)
            return
        }
        val state = worktreeStates.getOrPut(folder) { GitState(projectId, folder, git, scope, host) }
        if (folder !in tasksLoaded) loadTask(state, projectId, folder)
        active[projectId] = folder
        scope.launch {
            main.loadWorktrees()
            syncWorktrees(projectId)
        }
    }

    /** The task row comes from the database, so it is read off the UI thread and set when it arrives; a failed read is retried on the next switch. */
    private fun loadTask(state: GitState, projectId: Long, folder: Path) {
        scope.launch {
            val rows = withContext(Dispatchers.IO) { readTasks(projectId) }
            rows?.let {
                state.task = it.firstOrNull { row -> sameFolder(row.path(), folder) }
                tasksLoaded.add(folder)
            }
        }
    }

    /** Drops the states of worktrees the project no longer lists (removed in a terminal or by another tool); the project shows its main folder again if its active one went. */
    suspend fun syncWorktrees(projectId: Long) {
        val listed = states[projectId]?.worktrees?.takeIf { it.isNotEmpty() } ?: return
        val shown = worktreeStates.filterValues { it.projectId == projectId }.keys.toList()
        val gone = withContext(Dispatchers.IO) { shown.filter { folder -> listed.none { sameFolder(it.path(), folder) } } }
        gone.forEach(::dropWorktree)
    }

    private fun dropWorktree(folder: Path) {
        worktreeStates.remove(folder)?.close()
        tasksLoaded.remove(folder)
        active.entries.removeAll { it.value == folder }
    }

    /** A removed worktree: its state is closed, the project shows its main folder again if it was the active one, and the main folder re-reads its worktree list. */
    fun forgetWorktree(directory: Path) {
        val folder = FolderIdentity.of(directory)
        val projectId = worktreeStates[folder]?.projectId
        dropWorktree(folder)
        taskRowsChanged(projectId)
    }

    /** A task row was created or forgotten: views re-read the rows and the main folder its worktree list. */
    fun taskRowsChanged(projectId: Long?) {
        taskVersion++
        (projectId?.let { states[it] })?.let { main -> scope.launch { main.loadWorktrees() } }
    }

    /** The main folder and every linked worktree of the project with the given [tasks] rows; empty for an unknown project. */
    fun contextsOf(projectId: Long, tasks: List<TaskWorktree>): List<WorkContext> {
        val main = mainOf(projectId) ?: return emptyList()
        return workContexts(main.repo, main.worktrees, tasks, sessionDirectories(), active[projectId])
    }

    /** The project's task rows from the database (blocking); empty when they cannot be read. */
    fun taskRows(projectId: Long): List<TaskWorktree> = readTasks(projectId).orEmpty()

    private fun readTasks(projectId: Long): List<TaskWorktree>? =
        try {
            tasksOf(projectId)
        } catch (e: RuntimeException) {
            log.warn("Could not read the task worktrees of project {}: {}", projectId, e.javaClass.simpleName)
            null
        }

    /** Branch chip text for a session of the project running in a linked worktree; null for the main folder. */
    fun worktreeBadgeOf(projectId: Long, ccmSessionId: String): String? =
        worktreeBadge(mainOf(projectId)?.worktrees.orEmpty(), sessionDirectories()[ccmSessionId])

    /** The git states created so far, worktrees included; creates none. */
    fun loaded(): Collection<GitState> = states.values.toList() + worktreeStates.values

    /** Asks every project's review comments (all of them, no short-circuit) whether the finished turn was theirs. */
    fun anyReviewTurnEnded(previous: Map<String, AgentStatus>, sessions: List<SessionSnapshot>, queued: (String) -> List<Long>): Boolean =
        loaded().count { it.review.turnEnded(previous, sessions, queued) } > 0

    fun summaryOf(projectId: Long): GitSummary? = states[projectId]?.summary

    /** Branch of the project's main folder when its git state is already loaded. */
    fun branchOf(projectId: Long): String? = mainOf(projectId)?.status?.branch?.takeIf { it.isNotBlank() }

    /** Branch of the folder the session runs in: its linked worktree's branch, else the main folder's. */
    fun branchOf(projectId: Long, ccmSessionId: String): String? {
        val directory = sessionDirectories()[ccmSessionId]
        val linked = directory?.let { dir -> mainOf(projectId)?.worktrees.orEmpty().firstOrNull { !it.main() && sameFolder(it.path(), dir) } }
        return linked?.branch()?.takeIf { it.isNotBlank() } ?: branchOf(projectId)
    }

    fun refresh(projectId: Long) {
        states[projectId]?.refresh()
        worktreeStates.values.filter { it.projectId == projectId }.forEach(GitState::refresh)
    }

    /** Loads git state of projects not seen yet, so the sidebar shows branches before a project is opened. */
    fun loadNew(projectIds: Set<Long>) = loadOneByOne(projectIds.filter { it !in states }.mapNotNull(::mainOf), withWorktrees = true)

    /** Local state only (no fetch): the open project has its watcher, the others would otherwise go stale. */
    fun reloadUnopened(openProjectId: Long?, afterRunningSweep: Boolean = false) =
        loadOneByOne(states.filterKeys { it != openProjectId }.values.toList(), rerunIfBusy = afterRunningSweep)

    fun retain(projectIds: Set<Long>) {
        states.keys.filter { it !in projectIds }.forEach { states.remove(it)?.close() }
        worktreeStates.entries.removeAll { (_, state) -> (state.projectId !in projectIds).also { if (it) state.close() } }
        active.keys.removeAll { it !in projectIds }
        tasksLoaded.retainAll(worktreeStates.keys)
    }

    /** One project at a time, so many projects do not start dozens of git processes at once; a periodic reload is skipped while a sweep runs, a first load of new projects waits its turn. */
    private fun loadOneByOne(loaded: List<GitState>, withWorktrees: Boolean = false, rerunIfBusy: Boolean = false) {
        if (loaded.isEmpty()) return
        if (!withWorktrees && !rerunIfBusy && sweep.isLocked) return
        scope.launch { sweep.withLock { loaded.forEach { it.reloadNow(); if (withWorktrees) it.loadWorktrees() } } }
    }
}
