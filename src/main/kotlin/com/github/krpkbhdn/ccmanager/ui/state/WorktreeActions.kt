package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import java.nio.file.Path

/** What the Git tab's worktree operations do to the projects, sessions and runs around them. */
class WorktreeActions(
    private val projects: ProjectsState,
    private val sessions: SessionsState,
    private val stopRuns: (Long) -> Unit,
    private val hasLiveSessions: (Long) -> Boolean,
    private val navigate: (Route) -> Unit,
) {
    /** Opens the project registered for a worktree folder, registering it first when needed, and optionally starts a session there. */
    fun open(directory: Path, launchSession: Boolean) {
        val show = { id: Long ->
            navigate(Route.Project(id))
            if (launchSession) sessions.launch(id, LaunchMode.NEW)
        }
        projects.projectAt(directory)?.let { return show(it.id) }
        projects.register(directory, show)
    }

    /** The worktree becomes its own project, without navigating or starting a session. */
    fun register(directory: Path) {
        if (projects.projectAt(directory) == null) projects.register(directory) {}
    }

    fun hasRunningWork(directory: Path): Boolean = projects.projectAt(directory)?.let { hasLiveSessions(it.id) } == true

    /** Closes the sessions, shells and runs of the project at [directory] so nothing holds the folder while git removes it. */
    fun stopWork(directory: Path) {
        projects.projectAt(directory)?.let { projects.closeSessions(it); stopRuns(it.id) }
    }

    /** A removed worktree is no longer a place to work, so its project goes too; files and branches are not touched. */
    fun forget(directory: Path) {
        projects.projectAt(directory)?.let(projects::remove)
    }
}
