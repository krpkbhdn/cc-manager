package com.github.krpkbhdn.ccmanager.ui.sessions

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.Project
import java.time.Duration
import java.time.Instant

/** What the all-sessions screen shows; a null field means no restriction. [memberIds] are projects shown together with [projectId] (a group's members). */
data class SessionFilter(val status: AgentStatus? = null, val projectId: Long? = null, val memberIds: Set<Long> = emptySet()) {
    fun matches(session: SessionSnapshot): Boolean =
        (status == null || session.status == status) && (projectId == null || session.projectId == projectId || session.projectId in memberIds)

    /** The same filter that also takes the members of the selected project when it is a group. */
    fun withMembersFrom(projects: List<Project>): SessionFilter =
        copy(memberIds = projectId?.let { id -> projects.filter { it.groupId == id }.map { it.id }.toSet() } ?: emptySet())
}

const val UNKNOWN_PROJECT = "Unknown project"

/** One project's (or project group's own) sessions in the grouped view, with what its header flags. */
data class ProjectSessions(val projectId: Long, val name: String, val sessions: List<SessionSnapshot>, val waiting: Int, val stale: Int)

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

/** Projects offered by the project filter in [projectOrder]: those with sessions, a group whose members have sessions, plus the selected one so it can always be cleared. */
fun projectChoices(sessions: List<SessionSnapshot>, filter: SessionFilter, projects: List<Project>): List<Long> {
    val withSessions = sessions.map { it.projectId }.toSet()
    val groupsWithSessions = projects.filter { it.id in withSessions }.mapNotNull { it.groupId }.toSet()
    return projectOrder(projects).map { it.id }.filter { it in withSessions || it in groupsWithSessions || it == filter.projectId }
}

/** A working session without any event for longer than [threshold]; sessions without a known last event are never stale. */
fun isStale(session: SessionSnapshot, now: Instant, threshold: Duration): Boolean =
    session.status == AgentStatus.WORKING && session.lastEventAt?.let { Duration.between(it, now) > threshold } == true

private val byName = compareBy<Project, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }

/** Groups and ungrouped projects alphabetically, each group directly followed by its members alphabetically. */
fun projectOrder(projects: List<Project>): List<Project> =
    projects.filter { it.groupId == null }.sortedWith(byName)
        .flatMap { top -> listOf(top) + projects.filter { it.groupId == top.id }.sortedWith(byName) }

/** Filtered sessions per project in [projectOrder]; a member is named "group / member", entries without sessions are left out. */
fun groupByProject(sessions: List<SessionSnapshot>, filter: SessionFilter, projects: List<Project>, now: Instant, staleAfter: Duration): List<ProjectSessions> {
    val byProject = board(sessions, filter).groupBy { it.projectId }
    val names = projects.associate { it.id to it.name }
    fun entry(id: Long, name: String) = byProject[id]?.let { members ->
        ProjectSessions(id, name, members, members.count { it.status == AgentStatus.NEEDS_ACTION }, members.count { isStale(it, now, staleAfter) })
    }
    val known = projectOrder(projects).mapNotNull { project ->
        entry(project.id, project.groupId?.let { "${names[it]} / ${project.name}" } ?: project.name)
    }
    val unknown = byProject.keys.filter { it !in names }.sorted().mapNotNull { entry(it, UNKNOWN_PROJECT) }
    return known + unknown
}
