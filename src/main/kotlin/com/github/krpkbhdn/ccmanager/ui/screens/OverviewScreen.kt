package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.mostUrgent
import com.github.krpkbhdn.ccmanager.ui.state.needingAttention
import com.github.krpkbhdn.ccmanager.ui.state.overviewHeadline
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val cardMinWidth = 320.dp
private val gridPadding = 28.dp
private val heroDot = 12.dp

/** Mission control: what needs the user first, then every project at a glance. */
@Composable
fun OverviewScreen(state: AppState, modifier: Modifier = Modifier) {
    if (state.projects.all.isEmpty()) {
        OverviewEmpty(state::requestAddProject, modifier)
        return
    }
    val attention = needingAttention(state.live.sessions)
    val grid = rememberLazyGridState()
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cardMinWidth),
            modifier = Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(horizontal = gridPadding, vertical = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            fullWidth { OverviewHero(state, attention.size) }
            if (attention.isNotEmpty()) attentionCards(attention, state)
            fullWidth { SectionTitle("Projects", state.projects.topLevelCount) }
            items(state.projects.groups, key = { "group-${it.id}" }) { group ->
                GroupCard(
                    name = group.name,
                    status = state.live.statusOf(group.id),
                    members = state.projects.membersOf(group.id).map { state.memberSummary(it) },
                    onOpen = state.projectActions(group).onOpen,
                    modifier = Modifier.animateItem(),
                )
            }
            items(state.projects.ungrouped, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    status = state.live.statusOf(project.id),
                    sessions = state.live.of(project.id),
                    git = state.gitSummaryOf(project.id),
                    now = { state.now },
                    actions = state.projectActions(project),
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "add") { AddProjectCard(state::requestAddProject, Modifier.animateItem()) }
        }
        ThinScrollbar(grid)
    }
}

private fun AppState.memberSummary(member: Project): GroupMemberSummary {
    val urgent = mostUrgent(live.of(member.id))
    return GroupMemberSummary(member.name, live.statusOf(member.id), urgent?.let { it.activity ?: sessionTitle(it) }, projectActions(member).onOpen)
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.attentionCards(attention: List<SessionSnapshot>, state: AppState) {
    items(attention, key = { "attention-${it.ccmSessionId}" }, span = { GridItemSpan(maxLineSpan) }) { session ->
        AttentionCard(
            projectName = state.projects.nameOf(session.projectId),
            session = session,
            now = { state.now },
            onOpen = { state.answerProposals(session.ccmSessionId) },
            modifier = Modifier.animateItem(),
            proposalHeadline = state.proposals.headlineOf(session.ccmSessionId),
        )
    }
}

@Composable
private fun OverviewHero(state: AppState, attentionCount: Int) {
    val working = state.live.sessions.count { it.status == AgentStatus.WORKING }
    val answerable = state.live.sessions.count { state.proposals.of(it.ccmSessionId).isNotEmpty() }
    val headline = overviewHeadline(attentionCount, working, state.live.sessions.size, answerable)
    val status = when {
        attentionCount > 0 -> AgentStatus.NEEDS_ACTION
        working > 0 -> AgentStatus.WORKING
        else -> AgentStatus.IDLE
    }
    Row(Modifier.padding(bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md + 2.dp)) {
                Box(Modifier.size(heroDot * 2), contentAlignment = Alignment.Center) { StatusDot(status, size = heroDot) }
                Label(headline.title, style = Type.display, tooltipWhenTruncated = true)
            }
            Label(headline.subtitle, color = colors.textSecondary, style = Type.body)
        }
        AppButton("Add project", state::requestAddProject, icon = Icons.Plus)
    }
}

@Composable
internal fun SectionTitle(title: String, count: Int) {
    Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(title, style = Type.heading)
        CountBadge(count)
    }
}
