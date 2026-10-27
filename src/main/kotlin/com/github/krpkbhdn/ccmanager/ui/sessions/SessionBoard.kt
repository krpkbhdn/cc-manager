package com.github.krpkbhdn.ccmanager.ui.sessions

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Duration
import java.time.Instant

/** What the all-sessions screen shows; a null field means no restriction. */
data class SessionFilter(val status: AgentStatus? = null, val projectId: Long? = null) {
    fun matches(session: SessionSnapshot): Boolean =
        (status == null || session.status == status) && (projectId == null || session.projectId == projectId)
}

/** One project's sessions in the grouped view, with what its header flags. */
data class ProjectGroup(val projectId: Long, val name: String, val sessions: List<SessionSnapshot>, val waiting: Int, val stale: Int)

private val startOrder = compareBy<SessionSnapshot>({ it.startedAt }, { it.ccmSessionId })

/** Filtered sessions in a fixed order, oldest start first, so cards never jump when a status changes. */
fun board(sessions: List<SessionSnapshot>, filter: SessionFilter = SessionFilter()): List<SessionSnapshot> =
    sessions.filter(filter::matches).sortedWith(startOrder)

/** The waiting session after [currentId] in board order, wrapping around; the first one when [currentId] is not waiting. */
fun nextWaiting(sessions: List<SessionSnapshot>, currentId: String?): SessionSnapshot? {
    val waiting = board(sessions, SessionFilter(status = AgentStatus.NEEDS_ACTION))
    if (waiting.isEmpty()) return null
    val index = waiting.indexOfFirst { it.ccmSessionId == currentId }
    return waiting[(index + 1) % waiting.size]
}

/** Projects offered by the project filter in [projectOrder]: those with sessions, plus the selected one so it can always be cleared. */
fun projectChoices(sessions: List<SessionSnapshot>, filter: SessionFilter, projectOrder: List<Long>): List<Long> {
    val withSessions = sessions.map { it.projectId }.toSet()
    return projectOrder.filter { it in withSessions || it == filter.projectId }
}

/** A working session without any event for longer than [threshold]; sessions without a known last event are never stale. */
fun isStale(session: SessionSnapshot, now: Instant, threshold: Duration): Boolean =
    session.status == AgentStatus.WORKING && session.lastEventAt?.let { Duration.between(it, now) > threshold } == true

/** Filtered sessions grouped by project, projects alphabetical, only those that still have sessions. */
fun groupByProject(sessions: List<SessionSnapshot>, filter: SessionFilter, projectName: (Long) -> String, now: Instant, staleAfter: Duration): List<ProjectGroup> =
    board(sessions, filter).groupBy { it.projectId }
        .map { (projectId, members) ->
            ProjectGroup(projectId, projectName(projectId), members, members.count { it.status == AgentStatus.NEEDS_ACTION }, members.count { isStale(it, now, staleAfter) })
        }
        .sortedWith(compareBy<ProjectGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.projectId })
