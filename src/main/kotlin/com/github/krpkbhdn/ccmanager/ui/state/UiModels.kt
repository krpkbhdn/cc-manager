package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Snapshot of all live sessions plus the per-project traffic light. */
data class LiveSessions(
    val sessions: List<SessionSnapshot> = emptyList(),
    val projectStatus: Map<Long, AgentStatus> = emptyMap(),
) {
    fun of(projectId: Long): List<SessionSnapshot> = sessions.filter { it.projectId == projectId }
    fun statusOf(projectId: Long): AgentStatus = projectStatus[projectId] ?: AgentStatus.OFFLINE
    fun find(ccmSessionId: String): SessionSnapshot? = sessions.firstOrNull { it.ccmSessionId == ccmSessionId }
}

data class DiffState(val diff: GitDiff? = null, val loading: Boolean = false, val error: String? = null)

data class ToastItem(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val projectId: Long? = null,
    val ccmSessionId: String? = null,
)

data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean = true,
    val onConfirm: () -> Unit,
)

data class RenameRequest(val project: Project)

/** Coalesces bursts of change callbacks into sequential reloads (one consumer, latest request wins). */
class Refresher(scope: CoroutineScope, private val load: suspend () -> Unit) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { for (ignored in requests) load() }
    }

    fun request() {
        requests.trySend(Unit)
    }
}
