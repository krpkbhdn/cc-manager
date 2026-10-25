package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationsStateTest {
    private var windowActive = true
    private val state = NotificationsState({ "project-$it" }) { windowActive }

    private fun needsAction(id: Long, session: String) =
        AppNotification(id, Instant.now(), NotificationKind.NEEDS_ACTION, 1, session, "Needs you", "Permission needed")

    private fun snapshot(session: String, status: AgentStatus) =
        SessionSnapshot(session, 1, "c", status, "", "default", "m", null, Instant.now(), Instant.now(), emptyList(), 0, null)

    @Test
    fun `a session leaving needs action dismisses its toast and popup`() {
        state.receive(needsAction(1, "s1"))
        windowActive = false
        state.receive(needsAction(2, "s1"))
        assertThat(state.toasts).hasSize(1)
        assertThat(state.popups).hasSize(1)

        state.dismissResolvedAttention(mapOf("s1" to AgentStatus.NEEDS_ACTION), listOf(snapshot("s1", AgentStatus.WORKING)))

        assertThat(state.toasts).isEmpty()
        assertThat(state.popups).isEmpty()
    }

    @Test
    fun `toasts of other sessions, other kinds and still waiting sessions stay`() {
        state.receive(needsAction(1, "s-waiting"))
        state.receive(needsAction(2, "s-other"))
        state.receive(AppNotification(3, Instant.now(), NotificationKind.FINISHED, 1, "s-done", "Finished", "ok"))

        state.dismissResolvedAttention(
            mapOf("s-waiting" to AgentStatus.NEEDS_ACTION, "s-done" to AgentStatus.NEEDS_ACTION),
            listOf(snapshot("s-waiting", AgentStatus.NEEDS_ACTION), snapshot("s-done", AgentStatus.IDLE)),
        )

        assertThat(state.toasts.map { it.id }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `a received notification is deduplicated and prefixed with the project name`() {
        state.receive(needsAction(1, "s1"))
        state.receive(needsAction(1, "s1"))

        assertThat(state.items).hasSize(1)
        assertThat(state.toasts.single().title).startsWith("project-1: ")
        assertThat(state.unreadCount).isEqualTo(1)
    }
}
