package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot

sealed interface Route {
    data object Overview : Route
    data object Teams : Route
    data class Project(val id: Long) : Route
}

sealed interface ProjectTab {
    data class Session(val ccmSessionId: String) : ProjectTab
    data object Git : ProjectTab
    data object Activity : ProjectTab
}

/** Open terminal tabs and the selected tab of one project; every operation returns a new value. */
data class ProjectView(
    val openSessions: List<String> = emptyList(),
    val selected: ProjectTab = ProjectTab.Activity,
    val focusedSession: String? = null,
) {
    fun openSession(ccmSessionId: String): ProjectView = copy(
        openSessions = if (ccmSessionId in openSessions) openSessions else openSessions + ccmSessionId,
        selected = ProjectTab.Session(ccmSessionId),
        focusedSession = ccmSessionId,
    )

    fun select(tab: ProjectTab): ProjectView = when (tab) {
        is ProjectTab.Session -> openSession(tab.ccmSessionId)
        else -> copy(selected = tab)
    }

    /** Session tabs in strip order, then Git and Activity. */
    fun tabs(): List<ProjectTab> = openSessions.map { ProjectTab.Session(it) } + ProjectTab.Git + ProjectTab.Activity

    /** Selects the tab [step] places away, wrapping around the strip. */
    fun cycle(step: Int): ProjectView {
        val tabs = tabs()
        val current = tabs.indexOf(selected).coerceAtLeast(0)
        return select(tabs[Math.floorMod(current + step, tabs.size)])
    }

    fun closeSession(ccmSessionId: String): ProjectView {
        val index = openSessions.indexOf(ccmSessionId)
        if (index < 0) return this
        val remaining = openSessions - ccmSessionId
        val closedSelected = selected == ProjectTab.Session(ccmSessionId)
        val next = if (closedSelected) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.let { ProjectTab.Session(it) } ?: ProjectTab.Activity else selected
        return copy(
            openSessions = remaining,
            selected = next,
            focusedSession = focusedSession.takeUnless { it == ccmSessionId } ?: remaining.lastOrNull(),
        )
    }
}

/** Sessions needing the user first, then working, idle; stable inside one urgency. */
fun byUrgency(sessions: List<SessionSnapshot>): List<SessionSnapshot> =
    sessions.sortedByDescending { it.status.ordinal }

fun mostUrgent(sessions: List<SessionSnapshot>): SessionSnapshot? = byUrgency(sessions).firstOrNull()

fun needingAttention(sessions: List<SessionSnapshot>): List<SessionSnapshot> =
    sessions.filter { it.status == AgentStatus.NEEDS_ACTION }

/** Projects owning a session that just left WORKING/NEEDS_ACTION for IDLE, i.e. whose diff is likely stale. */
fun projectsBecameIdle(previous: Map<String, AgentStatus>, current: List<SessionSnapshot>): Set<Long> =
    current.filter { it.status == AgentStatus.IDLE && previous[it.ccmSessionId] in setOf(AgentStatus.WORKING, AgentStatus.NEEDS_ACTION) }
        .map { it.projectId }
        .toSet()
