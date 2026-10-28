package com.github.krpkbhdn.ccmanager.ui.sessions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.screens.SectionTitle
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.statusLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val cardMinWidth = 320.dp
private val gridPadding = 28.dp

/** Every session of every project as a card in start order, flat or grouped by project, filtered by status and project. */
@Composable
fun SessionsScreen(state: AppState, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(SessionFilter()) }
    val filter = selected.withMembersFrom(state.projects.all)
    var collapsed by remember { mutableStateOf(emptySet<Long>()) }
    val grouped = state.settings.groupSessionsByProject
    val sessions = state.live.sessions
    val shown = board(sessions, filter)
    val grid = rememberLazyGridState()
    val card = @Composable { session: SessionSnapshot, cardModifier: Modifier ->
        SessionCard(
            session = session,
            projectName = state.projects.nameOf(session.projectId),
            usage = state.sessionUsage[session.ccmSessionId],
            now = state.now,
            stale = isStale(session, state.now, state.workingStaleAfter),
            proposalHeadline = state.proposals.headlineOf(session.ccmSessionId),
            onOpen = { state.openSession(session.ccmSessionId) },
            onAnswer = { state.answerProposals(session.ccmSessionId) },
            modifier = cardModifier,
        )
    }
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cardMinWidth),
            modifier = Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(horizontal = gridPadding, vertical = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionTitle("All sessions", sessions.size) }
                        GroupToggle(grouped, state::setGroupSessionsByProject)
                    }
                    FilterBar(state, filter) { selected = it }
                }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { NoSessions(sessions.isEmpty()) }
            } else if (grouped) {
                groups(groupByProject(sessions, filter, state.projects.all, state.now, state.workingStaleAfter), collapsed, { id -> collapsed = if (id in collapsed) collapsed - id else collapsed + id }, card)
            } else {
                items(shown, key = { it.ccmSessionId }) { card(it, Modifier.animateItem()) }
            }
        }
        ThinScrollbar(grid)
    }
}

private fun LazyGridScope.groups(groups: List<ProjectSessions>, collapsed: Set<Long>, onToggle: (Long) -> Unit, card: @Composable (SessionSnapshot, Modifier) -> Unit) {
    groups.forEach { group ->
        val expanded = group.projectId !in collapsed
        item(key = "group-${group.projectId}", span = { GridItemSpan(maxLineSpan) }) { GroupHeader(group, expanded, { onToggle(group.projectId) }) }
        if (expanded) item(key = "row-${group.projectId}", span = { GridItemSpan(maxLineSpan) }) { GroupRow(group.sessions, card) }
    }
}

@Composable
private fun GroupToggle(grouped: Boolean, onChange: (Boolean) -> Unit) {
    SegmentedControl(listOf(Segment(false, "List", Icons.Grid), Segment(true, "By project", Icons.Layers)), grouped, onChange)
}

@Composable
private fun FilterBar(state: AppState, filter: SessionFilter, onChange: (SessionFilter) -> Unit) {
    val sessions = state.live.sessions
    val statuses = listOf<AgentStatus?>(null) + AgentStatus.entries.reversed()
    val projectIds = listOf<Long?>(null) + projectChoices(sessions, filter, state.projects.all)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        SegmentedControl(
            segments = statuses.map { status -> Segment(status, status?.let(::statusLabel) ?: "All", count = sessions.count { status == null || it.status == status }) },
            selected = filter.status,
            onSelect = { onChange(filter.copy(status = it)) },
        )
        SegmentedControl(
            segments = projectIds.map { id -> Segment(id, id?.let(state.projects::nameOf) ?: "All projects") },
            selected = filter.projectId,
            onSelect = { onChange(filter.copy(projectId = it)) },
            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun NoSessions(noneRunning: Boolean) {
    EmptyState(
        icon = Icons.Layers,
        title = if (noneRunning) "No sessions yet" else "No sessions match",
        message = if (noneRunning) "Start a Claude session in a project and it shows up here." else "Change the status or project filter to see more.",
        modifier = Modifier.fillMaxWidth(),
    )
}
