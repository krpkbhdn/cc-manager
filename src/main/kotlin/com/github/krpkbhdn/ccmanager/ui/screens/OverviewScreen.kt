package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SectionHeader
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.mostUrgent
import com.github.krpkbhdn.ccmanager.ui.state.needingAttention
import com.github.krpkbhdn.ccmanager.ui.state.pathTail
import com.github.krpkbhdn.ccmanager.ui.state.sessionCountLabel
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val cardMinWidth = 300.dp
private const val ABOUT_TEXT =
    "Claude Code Manager runs many local Claude Code sessions side by side. See at a glance which project is free, " +
        "working or waiting for you, jump into any terminal, review what changed and get notified when an agent needs a decision. " +
        "Everything stays on your machine and uses your own Claude Code installation."

@Composable
fun OverviewScreen(state: AppState, modifier: Modifier = Modifier) {
    if (state.projects.isEmpty()) {
        EmptyState(AppIcon.Folder, "Add your first project", ABOUT_TEXT, modifier) {
            PrimaryButton("Add project", state::addProject, icon = AppIcon.Plus)
            state.addError?.let { Label(it, color = colors.danger, size = FontSize.small, maxLines = 3) }
        }
        return
    }
    val attention = needingAttention(state.live.sessions)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        fullWidth { OverviewHeader(state.projects.size, state.live.sessions.size) }
        if (attention.isNotEmpty()) attentionSection(attention, state)
        fullWidth { SectionHeader("Projects", count = state.projects.size) }
        items(state.projects, key = { it.id }) { project ->
            val sessions = state.live.of(project.id)
            ProjectCard(project, state.live.statusOf(project.id), sessions) { state.navigate(Route.Project(project.id)) }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.attentionSection(attention: List<SessionSnapshot>, state: AppState) {
    fullWidth { SectionHeader("Needs your attention", count = attention.size) }
    items(attention, key = { it.ccmSessionId }, span = { GridItemSpan(maxLineSpan) }) { session ->
        AttentionRow(state.projectName(session.projectId), session) { state.openSession(session.ccmSessionId) }
    }
}

@Composable
private fun OverviewHeader(projectCount: Int, liveSessions: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label("Overview", size = FontSize.title, weight = FontWeight.SemiBold)
        Label("$projectCount projects, ${sessionCountLabel(liveSessions).lowercase()} live", color = colors.textSecondary)
    }
}

@Composable
private fun AttentionRow(projectName: String, session: SessionSnapshot, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    Pressable(onOpen, Modifier.fillMaxWidth().border(1.dp, colors.danger.copy(alpha = Alpha.BORDER_TINT), shape), shape, colors.danger.copy(alpha = Alpha.SOFT), colors.danger.copy(alpha = Alpha.SOFT * 2)) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatusDot(session.status)
            Column(Modifier.weight(1f)) {
                Label("$projectName  ·  ${sessionTitle(session)}", weight = FontWeight.SemiBold)
                Label(session.activity ?: "Waiting for your decision", color = colors.textSecondary, size = FontSize.small)
            }
            PrimaryButton("Open terminal", onOpen)
        }
    }
}

@Composable
private fun ProjectCard(project: Project, status: AgentStatus, sessions: List<SessionSnapshot>, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    val urgent = mostUrgent(sessions)
    Pressable(onOpen, Modifier.fillMaxWidth().border(1.dp, colors.border, shape), shape, colors.surface, colors.surfaceHover) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(project.name, size = FontSize.heading, weight = FontWeight.SemiBold)
                    Label(pathTail(project.path, 3), color = colors.textTertiary, size = FontSize.small)
                }
                StatusChip(status)
            }
            Label(
                urgent?.let { it.activity ?: sessionTitle(it) } ?: "No live sessions",
                color = if (urgent == null) colors.textTertiary else colors.textSecondary,
                size = FontSize.small,
                maxLines = 2,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(sessionCountLabel(sessions.size), color = colors.textSecondary, size = FontSize.small)
                Spacer(Modifier.weight(1f).width(Spacing.sm))
                SecondaryButton("Open", onOpen)
            }
        }
    }
}
