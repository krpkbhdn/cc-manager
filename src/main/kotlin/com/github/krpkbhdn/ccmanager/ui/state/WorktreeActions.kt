package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.git.TaskWorktreeService
import com.github.krpkbhdn.ccmanager.ui.run.RunState
import java.nio.file.Path

/** What the Git tab's worktree operations do to the sessions, shells and runs around them; a worktree is never a project. */
class WorktreeActions(
    private val projects: ProjectsState,
    private val sessions: SessionsState,
    private val shells: ShellsState,
    private val runs: RunState,
    private val taskWorktrees: TaskWorktreeService,
    private val terminal: TerminalPort,
    private val gits: () -> ProjectGits,
) {
    /** Switches the project's Git view to [directory] and starts a session there under that project when asked. */
    fun showWorktree(projectId: Long, directory: Path, launchSession: Boolean) {
        gits().switchTo(projectId, directory)
        if (launchSession) sessions.launchIn(projectId, directory, LaunchMode.NEW)
    }

    fun hasRunningWork(directory: Path): Boolean = sessions.hasLiveIn(directory) || shells.hasShellIn(directory) || runs.runningIn(directory).isNotEmpty()

    /** Closes the sessions, shells and runs in [directory] so nothing holds the folder while git removes it. */
    fun stopWork(directory: Path) {
        sessions.closeIn(directory)
        shells.closeIn(directory)
        runs.stopRunningIn(directory)
    }

    /** True while closed sessions and shells still have processes dying, which may keep their folder locked. */
    fun workStopping(): Boolean = terminal.closing()

    /** The stored part of forgetting a removed worktree (blocking: call it off the UI thread): its task row and a project registered at exactly that folder (older versions did). */
    fun forgetStored(directory: Path) {
        taskWorktrees.forget(directory)
        projects.projectAt(directory)?.let(projects::remove)
    }

    /** The in-memory part: a removed worktree is no longer a place to work; files and branches are not touched. */
    fun forget(directory: Path) = gits().forgetWorktree(directory)
}
