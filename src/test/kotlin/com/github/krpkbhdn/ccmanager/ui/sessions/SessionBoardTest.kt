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

    @Test
    fun `board and next waiting handle an empty list`() {
        assertThat(board(emptyList())).isEmpty()
        assertThat(nextWaiting(emptyList(), null)).isNull()
        assertThat(nextWaiting(emptyList(), "x")).isNull()
    }

    @Test
    fun `single waiting session is its own next`() {
        val one = listOf(session("only", AgentStatus.NEEDS_ACTION, 5))
        assertThat(board(one).map { it.ccmSessionId }).containsExactly("only")
        assertThat(nextWaiting(one, null)?.ccmSessionId).isEqualTo("only")
        assertThat(nextWaiting(one, "only")?.ccmSessionId).isEqualTo("only")
    }

    @Test
    fun `next waiting is null when the only sessions are not waiting even with a current id`() {
        assertThat(nextWaiting(listOf(session("a", AgentStatus.IDLE, 1)), "a")).isNull()
    }

    @Test
    fun `next waiting wraps from the last waiting session`() {
        val three = listOf(
            session("w1", AgentStatus.NEEDS_ACTION, 1),
            session("w2", AgentStatus.NEEDS_ACTION, 2),
            session("w3", AgentStatus.NEEDS_ACTION, 3),
        )
        assertThat(nextWaiting(three, "w3")?.ccmSessionId).isEqualTo("w1")
        assertThat(nextWaiting(three, "unknown")?.ccmSessionId).isEqualTo("w1")
    }

    @Test
    fun `filter without matches gives an empty board`() {
        assertThat(board(all, SessionFilter(projectId = 99))).isEmpty()
    }

    @Test
    fun `sort is stable for equal status and time`() {
        val ties = listOf(
            session("a", AgentStatus.IDLE, 5), session("b", AgentStatus.IDLE, 5), session("c", AgentStatus.IDLE, 5),
            session("x", AgentStatus.NEEDS_ACTION, 5), session("y", AgentStatus.NEEDS_ACTION, 5),
        )
        assertThat(board(ties).map { it.ccmSessionId }).containsExactly("x", "y", "a", "b", "c")
    }

    @Test
    fun `project choices follow project order and keep the selected project without sessions`() {
        val live = listOf(session("a", AgentStatus.IDLE, 1, projectId = 3), session("b", AgentStatus.IDLE, 2, projectId = 1))

        assertThat(projectChoices(live, SessionFilter(), listOf(1, 2, 3))).containsExactly(1, 3)
        assertThat(projectChoices(live, SessionFilter(projectId = 2), listOf(1, 2, 3))).containsExactly(1, 2, 3)
    }
}
