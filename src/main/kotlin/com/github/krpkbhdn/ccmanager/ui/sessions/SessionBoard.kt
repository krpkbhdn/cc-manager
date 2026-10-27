package com.github.krpkbhdn.ccmanager.ui.sessions

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant

/** What the all-sessions screen shows; a null field means no restriction. */
data class SessionFilter(val status: AgentStatus? = null, val projectId: Long? = null) {
    fun matches(session: SessionSnapshot): Boolean =
        (status == null || session.status == status) && (projectId == null || session.projectId == projectId)
}

private val longestWaitFirst = compareBy<SessionSnapshot> { it.lastEventAt ?: Instant.MAX }
private val mostRecentFirst = compareByDescending<SessionSnapshot> { it.lastEventAt ?: Instant.MIN }

/** Sessions needing the user first (longest wait first), then working, idle and offline, newest event first. */
fun board(sessions: List<SessionSnapshot>, filter: SessionFilter = SessionFilter()): List<SessionSnapshot> =
    sessions.filter(filter::matches)
        .sortedWith(compareByDescending<SessionSnapshot> { it.status.ordinal }.thenComparing { a, b ->
            if (a.status == AgentStatus.NEEDS_ACTION) longestWaitFirst.compare(a, b) else mostRecentFirst.compare(a, b)
        })

/** The waiting session after [currentId] in board order, wrapping around; the first one when [currentId] is not waiting. */
fun nextWaiting(sessions: List<SessionSnapshot>, currentId: String?): SessionSnapshot? {
    val waiting = board(sessions, SessionFilter(status = AgentStatus.NEEDS_ACTION))
    val index = waiting.indexOfFirst { it.ccmSessionId == currentId }
    return waiting.getOrNull((index + 1) % waiting.size.coerceAtLeast(1))
}
