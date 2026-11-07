package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.git.WorkContextActions
import com.github.krpkbhdn.ccmanager.ui.git.WorkSession
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The project's main folder and linked worktrees for the Git tab, with the app actions they trigger; keeps the worktree list and task rows loaded. */
@Composable
internal fun workContextActions(state: AppState, project: Project): WorkContextActions {
    val main = state.gits.mainOf(project.id)
    val active = state.gits.of(project.id)
    // The active worktree's own refreshes (create, remove) change what the main folder lists, so its list is re-read; the main folder itself would loop.
    LaunchedEffect(main, active?.takeIf { it !== main }?.worktrees) { main?.loadWorktrees() }
    LaunchedEffect(main?.worktrees) { state.gits.syncWorktrees(project.id) }
    val tasks by produceState(emptyList<TaskWorktree>(), main?.worktrees, state.gits.taskVersion) { value = withContext(Dispatchers.IO) { state.gits.taskRows(project.id) } }
    return WorkContextActions(
        contexts = state.gits.contextsOf(project.id, tasks),
        sessionOf = { id -> state.live.find(id)?.let { WorkSession(id, sessionTitle(it), it.status) } },
        switchTo = { state.gits.switchTo(project.id, it) },
        openSession = state::openSession,
        openSessionHere = { state.worktrees.showWorktree(project.id, it, launchSession = true) },
        openTerminalHere = { state.shells.openIn(project.id, it) },
    )
}
