package com.github.krpkbhdn.ccmanager.ui.sessions

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionBoardTest {
    private fun session(id: String, status: AgentStatus, at: Long, projectId: Long = 1) =
        SessionSnapshot(id, projectId, null, status, null, null, null, null, Instant.EPOCH, Instant.ofEpochSecond(at), emptyList(), 0, null)

    private val all = listOf(
        session("idle-old", AgentStatus.IDLE, 10),
        session("working", AgentStatus.WORKING, 50),
        session("offline", AgentStatus.OFFLINE, 90),
        session("idle-new", AgentStatus.IDLE, 80),
        session("wait-late", AgentStatus.NEEDS_ACTION, 70, projectId = 2),
        session("wait-early", AgentStatus.NEEDS_ACTION, 20),
    )

    @Test
    fun `board puts the longest waiting first then working idle and offline newest first`() {
        assertThat(board(all).map { it.ccmSessionId })
            .containsExactly("wait-early", "wait-late", "working", "idle-new", "idle-old", "offline")
    }

    @Test
    fun `board filters by status and project together`() {
        assertThat(board(all, SessionFilter(status = AgentStatus.IDLE)).map { it.ccmSessionId }).containsExactly("idle-new", "idle-old")
        assertThat(board(all, SessionFilter(projectId = 2)).map { it.ccmSessionId }).containsExactly("wait-late")
        assertThat(board(all, SessionFilter(AgentStatus.WORKING, projectId = 2))).isEmpty()
    }

    @Test
    fun `next waiting cycles through waiting sessions and wraps`() {
        assertThat(nextWaiting(all, null)?.ccmSessionId).isEqualTo("wait-early")
        assertThat(nextWaiting(all, "wait-early")?.ccmSessionId).isEqualTo("wait-late")
        assertThat(nextWaiting(all, "wait-late")?.ccmSessionId).isEqualTo("wait-early")
        assertThat(nextWaiting(all, "working")?.ccmSessionId).isEqualTo("wait-early")
    }

    @Test
    fun `next waiting is null when nothing waits`() {
        assertThat(nextWaiting(all.filter { it.status != AgentStatus.NEEDS_ACTION }, null)).isNull()
    }
}
