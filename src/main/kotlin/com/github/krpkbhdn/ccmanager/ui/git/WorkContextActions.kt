package com.github.krpkbhdn.ccmanager.ui.git

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.state.WorkContext
import java.nio.file.Path

/** The session working in a context, as the selector and the banner show it. */
data class WorkSession(val id: String, val title: String, val status: AgentStatus)

/** What the Git tab shows and does about the project's work contexts (main folder and linked worktrees); hoisted from the app state. */
class WorkContextActions(
    val contexts: List<WorkContext>,
    val sessionOf: (String) -> WorkSession?,
    /** Shows [Path] (a worktree) in the Git tab; null shows the main folder. */
    val switchTo: (Path?) -> Unit,
    val openSession: (String) -> Unit,
    val openSessionHere: (Path) -> Unit,
    val openTerminalHere: (Path) -> Unit,
) {
    val active: WorkContext? get() = contexts.firstOrNull { it.active }

    companion object {
        val NONE = WorkContextActions(emptyList(), { null }, {}, {}, {}, {})
    }
}
