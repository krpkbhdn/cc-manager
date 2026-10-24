package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.DiffState
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.ProjectView
import com.github.krpkbhdn.ccmanager.ui.state.byUrgency
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle

private const val ENDED_SESSION_LABEL = "Ended session"

@Composable
fun ProjectScreen(state: AppState, project: Project, modifier: Modifier = Modifier) {
    val view = state.viewOf(project.id)
    LaunchedEffect(project.id) { state.refreshBackgroundAgents(project.id) }
    Column(modifier.fillMaxSize()) {
        ProjectHeader(
            project = project,
            status = state.live.statusOf(project.id),
            ideAvailable = state.ideAvailable,
            launching = state.launching,
            actions = ProjectHeaderActions(
                onLaunch = { state.launch(project.id, it) },
                onOpenFolder = { state.openFolder(project) },
                onOpenIde = { state.openIde(project) },
                onShowDiff = { state.select(project.id, ProjectTab.Diff) },
            ),
        )
        Row(Modifier.fillMaxSize()) {
            AgentsPanel(
                sessions = byUrgency(state.live.of(project.id)),
                selectedSessionId = (view.selected as? ProjectTab.Session)?.ccmSessionId,
                now = state.now,
                background = state.backgroundAgents[project.id].orEmpty(),
                actions = AgentsPanelActions(
                    onOpenSession = { state.select(project.id, ProjectTab.Session(it)) },
                    onRefreshBackground = { state.refreshBackgroundAgents(project.id) },
                    onAttach = { state.attach(project.id, it) },
                ),
            )
            ProjectMainArea(state, project, view, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProjectMainArea(state: AppState, project: Project, view: ProjectView, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        ProjectTabStrip(
            tabs = tabSpecs(state, view),
            selected = view.selected,
            onSelect = { state.select(project.id, it) },
            onClose = { (it as? ProjectTab.Session)?.let { session -> state.requestCloseSession(project.id, session.ccmSessionId) } },
        )
        when (val tab = view.selected) {
            is ProjectTab.Session -> SessionTab(state, tab.ccmSessionId)
            ProjectTab.Diff -> DiffTabHost(state, project)
            ProjectTab.Activity -> ActivityTabHost(state, view, project)
        }
    }
}

private fun tabSpecs(state: AppState, view: ProjectView): List<TabSpec> {
    val sessions = view.openSessions.map { id ->
        val snapshot = state.live.find(id)
        TabSpec(ProjectTab.Session(id), snapshot?.let(::sessionTitle) ?: ENDED_SESSION_LABEL, snapshot?.status ?: AgentStatus.OFFLINE, null, closable = true)
    }
    return sessions + listOf(
        TabSpec(ProjectTab.Diff, "Diff", null, AppIcon.Diff, closable = false),
        TabSpec(ProjectTab.Activity, "Activity", null, AppIcon.Activity, closable = false),
    )
}

@Composable
private fun SessionTab(state: AppState, ccmSessionId: String) {
    Column(Modifier.fillMaxSize()) {
        QuickActionsBar(enabled = state.live.find(ccmSessionId) != null, onSend = { state.send(ccmSessionId, it) })
        HorizontalRule()
        state.terminal.View(ccmSessionId, Modifier.fillMaxSize())
    }
}

@Composable
private fun DiffTabHost(state: AppState, project: Project) {
    LaunchedEffect(project.id) { if (project.id !in state.diffs) state.refreshDiff(project.id) }
    DiffTab(state.diffs[project.id] ?: DiffState(loading = true), { state.refreshDiff(project.id) })
}

@Composable
private fun ActivityTabHost(state: AppState, view: ProjectView, project: Project) {
    val target = view.focusedSession?.takeIf { state.live.find(it) != null } ?: byUrgency(state.live.of(project.id)).firstOrNull()?.ccmSessionId
    val entries by produceState(emptyList<ActivityEntry>(), target, state.live) {
        value = target?.let { state.activityOf(it) }.orEmpty()
    }
    ActivityTab(entries, hasSession = target != null)
}
