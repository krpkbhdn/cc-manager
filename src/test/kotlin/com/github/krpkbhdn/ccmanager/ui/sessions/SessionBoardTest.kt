package com.github.krpkbhdn.ccmanager.ui.sessions

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import java.nio.file.Path
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

    private fun project(id: Long, name: String, group: Boolean = false, groupId: Long? = null) =
        Project(id, name, Path.of(name), now, ModelSelection.DEFAULT, group, groupId)

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

        val projects = listOf(project(1, "a"), project(2, "b"), project(3, "c"))

        assertThat(projectChoices(live, SessionFilter(), projects)).containsExactly(1, 3)
        assertThat(projectChoices(live, SessionFilter(projectId = 2), projects)).containsExactly(1, 2, 3)
    }

    @Test
    fun `a group is a project choice when only its members have sessions`() {
        val projects = listOf(project(1, "grp", group = true), project(2, "m", groupId = 1), project(3, "other", group = true))
        val live = listOf(session("a", AgentStatus.IDLE, 1, projectId = 2))

        assertThat(projectChoices(live, SessionFilter(), projects)).containsExactly(1, 2)
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
        val projects = listOf(project(1, "zeta"), project(2, "Alpha"))

        val groups = groupByProject(sessions, SessionFilter(), projects, now, staleAfter)

        assertThat(groups.map { it.name }).containsExactly("Alpha", "zeta")
        assertThat(ids(groups[1].sessions)).containsExactly("z2", "z1")
        assertThat(groups[1].waiting).isEqualTo(1)
        assertThat(groups[1].stale).isEqualTo(1)
        assertThat(groups[0].waiting).isZero()
    }

    @Test
    fun `groups keep only projects that have sessions after filtering`() {
        val sessions = listOf(session("a", AgentStatus.IDLE, 1, projectId = 1), session("b", AgentStatus.NEEDS_ACTION, 2, projectId = 2))

        val groups = groupByProject(sessions, SessionFilter(status = AgentStatus.NEEDS_ACTION), listOf(project(1, "p1"), project(2, "p2")), now, staleAfter)

        assertThat(groups.map { it.projectId }).containsExactly(2L)
    }

    @Test
    fun `a group lists its own sessions then its members and keeps ungrouped projects in alphabetical order`() {
        val projects = listOf(
            project(1, "zeta"), project(100, "suite", group = true), project(3, "web", groupId = 100),
            project(2, "api", groupId = 100), project(4, "beta"), project(5, "idle-member", groupId = 100),
        )
        val sessions = listOf(
            session("m-web", AgentStatus.IDLE, 1, projectId = 3), session("g", AgentStatus.WORKING, 2, projectId = 100),
            session("m-api", AgentStatus.NEEDS_ACTION, 3, projectId = 2), session("z", AgentStatus.IDLE, 4, projectId = 1),
            session("b", AgentStatus.IDLE, 5, projectId = 4),
        )

        val groups = groupByProject(sessions, SessionFilter(), projects, now, staleAfter)

        assertThat(groups.map { it.name }).containsExactly("beta", "suite", "suite / api", "suite / web", "zeta")
        assertThat(groups[2].waiting).isEqualTo(1)
    }

    @Test
    fun `filtering by a group also shows its members and no other project`() {
        val projects = listOf(project(100, "suite", group = true), project(2, "api", groupId = 100), project(3, "other"))
        val sessions = listOf(session("g", AgentStatus.IDLE, 1, projectId = 100), session("m", AgentStatus.IDLE, 2, projectId = 2), session("o", AgentStatus.IDLE, 3, projectId = 3))

        assertThat(ids(board(sessions, SessionFilter(projectId = 100).withMembersFrom(projects)))).containsExactly("g", "m")
        assertThat(ids(board(sessions, SessionFilter(projectId = 2).withMembersFrom(projects)))).containsExactly("m")
    }

    @Test
    fun `sessions of an unregistered project stay visible under Unknown project`() {
        val sessions = listOf(session("a", AgentStatus.IDLE, 1, projectId = 1), session("x", AgentStatus.IDLE, 2, projectId = 99))

        val groups = groupByProject(sessions, SessionFilter(), listOf(project(1, "p1")), now, staleAfter)

        assertThat(groups.map { it.name }).containsExactly("p1", "Unknown project")
        assertThat(ids(groups[1].sessions)).containsExactly("x")
    }

    @Test
    fun `project order puts members right after their group`() {
        val projects = listOf(project(2, "b-member", groupId = 100), project(100, "grp", group = true), project(1, "a"), project(3, "a-member", groupId = 100))

        assertThat(projectOrder(projects).map { it.id }).containsExactly(1L, 100L, 3L, 2L)
    }
}
