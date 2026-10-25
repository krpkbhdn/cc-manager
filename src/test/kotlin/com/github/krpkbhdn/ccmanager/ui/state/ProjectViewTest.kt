package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectViewTest {
    private fun session(id: String, status: AgentStatus, projectId: Long = 1) =
        SessionSnapshot(id, projectId, null, status, null, null, null, null, Instant.EPOCH, Instant.EPOCH, emptyList(), 0, null)

    @Test
    fun `opening a session selects and focuses it once`() {
        val view = ProjectView().openSession("a").openSession("b").openSession("a")

        assertThat(view.openSessions).containsExactly("a", "b")
        assertThat(view.selected).isEqualTo(ProjectTab.Session("a"))
        assertThat(view.focusedSession).isEqualTo("a")
    }

    @Test
    fun `selecting diff keeps open sessions`() {
        val view = ProjectView().openSession("a").select(ProjectTab.Git)

        assertThat(view.selected).isEqualTo(ProjectTab.Git)
        assertThat(view.openSessions).containsExactly("a")
    }

    @Test
    fun `cycling walks sessions then diff and activity and wraps`() {
        val view = ProjectView().openSession("a").openSession("b")

        assertThat(view.tabs()).containsExactly(ProjectTab.Session("a"), ProjectTab.Session("b"), ProjectTab.Git, ProjectTab.Run, ProjectTab.Activity)
        assertThat(view.cycle(1).selected).isEqualTo(ProjectTab.Git)
        assertThat(view.cycle(-1).selected).isEqualTo(ProjectTab.Session("a"))
        assertThat(view.select(ProjectTab.Activity).cycle(1).selected).isEqualTo(ProjectTab.Session("a"))
    }

    @Test
    fun `closing the selected tab selects its neighbour`() {
        val view = ProjectView().openSession("a").openSession("b").openSession("c").select(ProjectTab.Session("b")).closeSession("b")

        assertThat(view.openSessions).containsExactly("a", "c")
        assertThat(view.selected).isEqualTo(ProjectTab.Session("c"))
    }

    @Test
    fun `closing the last session falls back to activity`() {
        val view = ProjectView().openSession("a").closeSession("a")

        assertThat(view.selected).isEqualTo(ProjectTab.Activity)
        assertThat(view.focusedSession).isNull()
    }

    @Test
    fun `closing an unselected tab keeps the selection`() {
        val view = ProjectView().openSession("a").openSession("b").closeSession("a")

        assertThat(view.selected).isEqualTo(ProjectTab.Session("b"))
    }

    @Test
    fun `sessions are ordered by urgency`() {
        val sessions = listOf(session("idle", AgentStatus.IDLE), session("needs", AgentStatus.NEEDS_ACTION), session("work", AgentStatus.WORKING))

        assertThat(byUrgency(sessions).map { it.ccmSessionId }).containsExactly("needs", "work", "idle")
        assertThat(mostUrgent(sessions)?.ccmSessionId).isEqualTo("needs")
        assertThat(needingAttention(sessions).map { it.ccmSessionId }).containsExactly("needs")
        assertThat(mostUrgent(emptyList())).isNull()
    }

    @Test
    fun `projects become idle only on a transition from busy`() {
        val previous = mapOf("a" to AgentStatus.WORKING, "b" to AgentStatus.IDLE, "c" to AgentStatus.NEEDS_ACTION)
        val current = listOf(session("a", AgentStatus.IDLE, 1), session("b", AgentStatus.IDLE, 2), session("c", AgentStatus.IDLE, 3), session("d", AgentStatus.IDLE, 4))

        assertThat(projectsBecameIdle(previous, current)).containsExactlyInAnyOrder(1L, 3L)
    }
}
