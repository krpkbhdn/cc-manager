package com.github.krpkbhdn.ccmanager.ui.sessions

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionBoardTest {
    private val now = Instant.ofEpochSecond(10_000)
    private val staleAfter = Duration.ofMinutes(10)

    private fun session(id: String, status: AgentStatus, started: Long, projectId: Long = 1, lastEvent: Instant? = Instant.ofEpochSecond(started)) =
        SessionSnapshot(id, projectId, null, status, null, null, null, null, Instant.ofEpochSecond(started), lastEvent, emptyList(), 0, null)

    private val all = listOf(
        session("idle-old", AgentStatus.IDLE, 10),
        session("working", AgentStatus.WORKING, 50),
        session("offline", AgentStatus.OFFLINE, 90),
        session("idle-new", AgentStatus.IDLE, 80),
        session("wait-late", AgentStatus.NEEDS_ACTION, 70, projectId = 2),
        session("wait-early", AgentStatus.NEEDS_ACTION, 20),
    )

    private fun ids(sessions: List<SessionSnapshot>) = sessions.map { it.ccmSessionId }

    @Test
    fun `board orders by start time whatever the status`() {
        assertThat(ids(board(all))).containsExactly("idle-old", "wait-early", "working", "wait-late", "idle-new", "offline")
    }

    @Test
    fun `board breaks equal start times by session id and ignores the last event`() {
        val tied = listOf(
            session("b", AgentStatus.NEEDS_ACTION, 5, lastEvent = Instant.ofEpochSecond(1)),
            session("a", AgentStatus.IDLE, 5, lastEvent = Instant.ofEpochSecond(999)),
        )

        assertThat(ids(board(tied))).containsExactly("a", "b")
    }

    @Test
    fun `board filters by status and project together`() {
        assertThat(ids(board(all, SessionFilter(status = AgentStatus.IDLE)))).containsExactly("idle-old", "idle-new")
        assertThat(ids(board(all, SessionFilter(projectId = 2)))).containsExactly("wait-late")
        assertThat(board(all, SessionFilter(AgentStatus.WORKING, projectId = 2))).isEmpty()
    }

    @Test
    fun `next waiting follows the fixed order and wraps`() {
        assertThat(nextWaiting(all, null)?.ccmSessionId).isEqualTo("wait-early")
        assertThat(nextWaiting(all, "wait-early")?.ccmSessionId).isEqualTo("wait-late")
        assertThat(nextWaiting(all, "wait-late")?.ccmSessionId).isEqualTo("wait-early")
        assertThat(nextWaiting(all, "working")?.ccmSessionId).isEqualTo("wait-early")
    }

    @Test
    fun `next waiting is null when nothing waits`() {
        assertThat(nextWaiting(all.filter { it.status != AgentStatus.NEEDS_ACTION }, null)).isNull()
        assertThat(nextWaiting(emptyList(), "x")).isNull()
    }

    @Test
    fun `single waiting session is its own next`() {
        val one = listOf(session("only", AgentStatus.NEEDS_ACTION, 5))

        assertThat(nextWaiting(one, "only")?.ccmSessionId).isEqualTo("only")
    }

    @Test
    fun `project choices follow project order and keep the selected project without sessions`() {
        val live = listOf(session("a", AgentStatus.IDLE, 1, projectId = 3), session("b", AgentStatus.IDLE, 2, projectId = 1))

        assertThat(projectChoices(live, SessionFilter(), listOf(1, 2, 3))).containsExactly(1, 3)
        assertThat(projectChoices(live, SessionFilter(projectId = 2), listOf(1, 2, 3))).containsExactly(1, 2, 3)
    }

    @Test
    fun `only a working session silent for longer than the threshold is stale`() {
        val silent = now.minus(staleAfter.plusSeconds(1))
        AgentStatus.entries.forEach { status ->
            assertThat(isStale(session("s", status, 1, lastEvent = silent), now, staleAfter)).isEqualTo(status == AgentStatus.WORKING)
        }
    }

    @Test
    fun `staleness starts strictly after the threshold and needs a known last event`() {
        assertThat(isStale(session("s", AgentStatus.WORKING, 1, lastEvent = now.minus(staleAfter)), now, staleAfter)).isFalse()
        assertThat(isStale(session("s", AgentStatus.WORKING, 1, lastEvent = now.minus(staleAfter).minusMillis(1)), now, staleAfter)).isTrue()
        assertThat(isStale(session("s", AgentStatus.WORKING, 1, lastEvent = null), now, staleAfter)).isFalse()
    }

    @Test
    fun `groups are alphabetical by project name and count waiting and stale sessions`() {
        val silent = now.minus(Duration.ofHours(1))
        val sessions = listOf(
            session("z1", AgentStatus.NEEDS_ACTION, 2, projectId = 1),
            session("z2", AgentStatus.WORKING, 1, projectId = 1, lastEvent = silent),
            session("a1", AgentStatus.IDLE, 3, projectId = 2),
        )
        val names = mapOf(1L to "zeta", 2L to "Alpha")

        val groups = groupByProject(sessions, SessionFilter(), { names.getValue(it) }, now, staleAfter)

        assertThat(groups.map { it.name }).containsExactly("Alpha", "zeta")
        assertThat(ids(groups[1].sessions)).containsExactly("z2", "z1")
        assertThat(groups[1].waiting).isEqualTo(1)
        assertThat(groups[1].stale).isEqualTo(1)
        assertThat(groups[0].waiting).isZero()
    }

    @Test
    fun `groups keep only projects that have sessions after filtering`() {
        val sessions = listOf(session("a", AgentStatus.IDLE, 1, projectId = 1), session("b", AgentStatus.NEEDS_ACTION, 2, projectId = 2))

        val groups = groupByProject(sessions, SessionFilter(status = AgentStatus.NEEDS_ACTION), { "p$it" }, now, staleAfter)

        assertThat(groups.map { it.projectId }).containsExactly(2L)
    }
}
