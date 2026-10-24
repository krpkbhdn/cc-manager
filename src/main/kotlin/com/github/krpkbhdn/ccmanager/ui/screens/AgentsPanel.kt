package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgent
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pill
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SectionHeader
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.state.backgroundTasksLabel
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.state.permissionModeLabel
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Duration
import java.time.Instant

private val panelWidth = 320.dp
private const val PLAN_MODE = "plan"
private const val BYPASS_MODE = "bypassPermissions"

class AgentsPanelActions(val onOpenSession: (String) -> Unit, val onRefreshBackground: () -> Unit, val onAttach: (String) -> Unit)

@Composable
fun AgentsPanel(
    sessions: List<SessionSnapshot>,
    selectedSessionId: String?,
    now: Instant,
    background: List<BackgroundAgent>,
    actions: AgentsPanelActions,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxHeight()) {
        LazyColumn(
            Modifier.width(panelWidth).fillMaxHeight(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionHeader("Agents", count = sessions.size, modifier = Modifier.padding(bottom = Spacing.xs)) }
            if (sessions.isEmpty()) item { Label("No live sessions. Launch Claude to start one.", color = colors.textTertiary, size = FontSize.small, maxLines = 3) }
            items(sessions, key = { it.ccmSessionId }) { session ->
                SessionCard(session, session.ccmSessionId == selectedSessionId, now) { actions.onOpenSession(session.ccmSessionId) }
            }
            item {
                SectionHeader("Background agents", Modifier.padding(top = Spacing.lg)) { IconAction(AppIcon.Refresh, actions.onRefreshBackground) }
            }
            if (background.isEmpty()) item { Label("None running.", color = colors.textTertiary, size = FontSize.small) }
            items(background, key = { it.id }) { agent -> BackgroundAgentCard(agent) { actions.onAttach(agent.id) } }
        }
        VerticalRule()
    }
}

@Composable
private fun SessionCard(session: SessionSnapshot, selected: Boolean, now: Instant, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    val borderColor = if (selected) colors.accent else if (session.status == AgentStatus.NEEDS_ACTION) colors.danger.copy(alpha = Alpha.BORDER_TINT) else colors.border
    Pressable(onClick, Modifier.fillMaxWidth().border(1.dp, borderColor, shape), shape, if (selected) colors.surfaceSelected else colors.surface, if (selected) colors.surfaceSelected else colors.surfaceHover) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusDot(session.status)
                Label(sessionTitle(session), Modifier.weight(1f), weight = FontWeight.SemiBold)
                permissionModeLabel(session.permissionMode)?.let { Pill(it, modeTint(session.permissionMode)) }
            }
            Label(metaLine(session, now), color = colors.textTertiary, size = FontSize.caption)
            session.activity?.let { Label(it, color = colors.textSecondary, size = FontSize.small, maxLines = 2) }
            session.error?.let { Label(it, color = colors.danger, size = FontSize.small, maxLines = 2) }
            if (session.backgroundTasks > 0) Pill(backgroundTasksLabel(session.backgroundTasks), colors.accent)
            session.subagents.forEach { SubagentRow(it) }
        }
    }
}

@Composable
private fun modeTint(mode: String?): Color = when (mode) {
    PLAN_MODE -> colors.accent
    BYPASS_MODE -> colors.danger
    else -> colors.textSecondary
}

private fun metaLine(session: SessionSnapshot, now: Instant): String {
    val age = session.lastEventAt?.let { "${formatElapsed(Duration.between(it, now))} ago" }
    return listOfNotNull(session.model, age).joinToString("  ·  ")
}

@Composable
private fun SubagentRow(subagent: SubagentSnapshot) {
    val status = if (subagent.running) AgentStatus.WORKING else AgentStatus.OFFLINE
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs).background(colors.background.copy(alpha = Alpha.DISABLED), RoundedCornerShape(Radius.sm)).padding(Spacing.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatusDot(status, Modifier.padding(top = 1.dp), size = 6.dp)
        Column(Modifier.weight(1f)) {
            Label(subagent.agentType.ifBlank { "agent" }, size = FontSize.small, weight = FontWeight.Medium)
            subagent.activity?.let { Label(it, color = colors.textSecondary, size = FontSize.caption, maxLines = 2) }
        }
        Label(if (subagent.running) "running" else "finished", color = colors.textTertiary, size = FontSize.caption)
    }
}

@Composable
private fun BackgroundAgentCard(agent: BackgroundAgent, onAttach: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier.fillMaxWidth().border(1.dp, colors.border, shape).background(colors.surface, shape).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Label(agent.name.ifBlank { agent.id }, weight = FontWeight.Medium)
            Label(listOfNotNull(agent.status, agent.summary).joinToString("  ·  "), color = colors.textTertiary, size = FontSize.caption, maxLines = 2)
        }
        SecondaryButton("Attach", onAttach)
    }
}
