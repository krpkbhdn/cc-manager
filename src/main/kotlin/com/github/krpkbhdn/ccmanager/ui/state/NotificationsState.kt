package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind

private const val NOTIFICATION_CAP = 100
private const val MAX_TOASTS = 4
private const val ACTION_FAILED_TITLE = "Action failed"

/** Notification history, unread marker and the in-app toasts and desktop popups derived from it. */
class NotificationsState(
    private val projectName: (Long) -> String,
    private val windowActive: () -> Boolean,
) {
    var items by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var readUpTo by mutableLongStateOf(0L)
        private set
    var toasts by mutableStateOf<List<ToastItem>>(emptyList())
        private set
    var popups by mutableStateOf<List<ToastItem>>(emptyList())
        private set
    private var toastCounter = 0L

    val unreadCount: Int get() = items.count { it.id > readUpTo }

    fun restore(history: List<AppNotification>) {
        items = history.takeLast(NOTIFICATION_CAP)
    }

    fun markAllRead() {
        readUpTo = items.maxOfOrNull { it.id } ?: readUpTo
    }

    fun dismissToast(id: Long) {
        toasts = toasts.filterNot { it.id == id }
        popups = popups.filterNot { it.id == id }
    }

    fun toast(kind: NotificationKind, title: String, body: String) = pushToast(ToastItem(nextToastId(), kind, title, body))

    fun reportError(message: String) = reportError(ACTION_FAILED_TITLE, message)

    fun reportError(title: String, message: String) = toast(NotificationKind.ERROR, title, message)

    fun receive(notification: AppNotification) {
        if (items.any { it.id == notification.id }) return
        items = (items + notification).takeLast(NOTIFICATION_CAP)
        val toast = ToastItem(
            id = notification.id,
            kind = notification.kind,
            title = "${projectName(notification.projectId)}: ${notification.title}",
            body = notification.body,
            projectId = notification.projectId,
            ccmSessionId = notification.ccmSessionId,
        )
        when (toastPlaceFor(windowActive())) {
            ToastPlace.IN_APP -> pushToast(toast)
            ToastPlace.DESKTOP_POPUP -> popups = (popups + toast).takeLast(MAX_TOASTS)
        }
    }

    /** A toast asking for the user is stale once its session left NEEDS_ACTION, whoever answered. */
    fun dismissResolvedAttention(previous: Map<String, AgentStatus>, current: List<SessionSnapshot>) {
        val resolved = current.filter { previous[it.ccmSessionId] == AgentStatus.NEEDS_ACTION && it.status != AgentStatus.NEEDS_ACTION }.map { it.ccmSessionId }.toSet()
        if (resolved.isEmpty()) return
        val stale = { toast: ToastItem -> toast.kind == NotificationKind.NEEDS_ACTION && toast.ccmSessionId in resolved }
        toasts = toasts.filterNot(stale)
        popups = popups.filterNot(stale)
    }

    private fun nextToastId() = -(++toastCounter)

    private fun pushToast(toast: ToastItem) {
        toasts = (toasts + toast).takeLast(MAX_TOASTS)
    }
}
