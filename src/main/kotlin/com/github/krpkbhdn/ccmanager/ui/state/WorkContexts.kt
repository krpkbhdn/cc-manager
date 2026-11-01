package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.ui.git.model.sameFolder
import java.nio.file.Path

/** One place to work in a project: its main folder or a linked worktree, with the task and the live session there if any. */
data class WorkContext(
    val directory: Path,
    val branch: String?,
    val main: Boolean,
    val dirty: Boolean,
    val task: TaskWorktree?,
    val sessionId: String?,
    val active: Boolean,
)

/**
 * Review comments go to the sessions running in the shown folder: the active worktree [directory] or, for null, [mainFolder].
 * A worktree never falls back to other sessions (the caller starts a new one there); a session with no recorded folder
 * counts as running in the main folder.
 */
fun sessionsWorkingIn(sessions: List<SessionSnapshot>, sessionDirectories: Map<String, Path>, directory: Path?, mainFolder: Path): List<SessionSnapshot> =
    sessions.filter { s ->
        val where = sessionDirectories[s.ccmSessionId]
        if (directory == null) where == null || sameFolder(where, mainFolder) else where != null && sameFolder(where, directory)
    }

/** The main folder first, then the linked worktrees; [active] null means the main folder. [worktrees] empty (not loaded yet) still yields the main folder. */
fun workContexts(
    mainFolder: Path,
    worktrees: List<Worktree>,
    tasks: List<TaskWorktree>,
    sessionDirectories: Map<String, Path>,
    active: Path?,
): List<WorkContext> {
    val entries = worktrees.sortedByDescending { it.main() }.ifEmpty { listOf(Worktree(mainFolder, "", "", true, false, false, false, false, false)) }
    return entries.map { entry ->
        val directory = entry.path()
        WorkContext(
            directory = directory,
            branch = entry.branch().takeIf { it.isNotBlank() },
            main = entry.main(),
            dirty = entry.dirty(),
            task = tasks.firstOrNull { sameFolder(it.path(), directory) },
            sessionId = sessionDirectories.entries.firstOrNull { sameFolder(it.value, directory) }?.key,
            active = if (active == null) entry.main() else sameFolder(active, directory),
        )
    }
}

/** Branch chip text of a session working in [directory] when that is a linked worktree of [worktrees]; null in the main folder or elsewhere. */
fun worktreeBadge(worktrees: List<Worktree>, directory: Path?): String? {
    if (directory == null) return null
    val linked = worktrees.firstOrNull { !it.main() && sameFolder(it.path(), directory) } ?: return null
    return linked.branch().takeIf { it.isNotBlank() } ?: linked.path().fileName?.toString()
}
