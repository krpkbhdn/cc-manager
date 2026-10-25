package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgent
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.ui.teams.RoleAvatar
import com.github.krpkbhdn.ccmanager.ui.teams.RoleChip
import com.github.krpkbhdn.ccmanager.ui.teams.slotOf
import com.github.krpkbhdn.ccmanager.ui.teams.subagentRole
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.backgroundTasksLabel
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.state.permissionModeLabel
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Duration
import java.time.Instant

val agentsPanelWidth = 340.dp
private const val PLAN_MODE = "plan"
private const val BYPASS_MODE = "bypassPermissions"
private const val SKELETON_ROWS = 2
private val skeletonRowHeight = 52.dp

class AgentsPanelActions(
    val onOpenSession: (String) -> Unit,
    val onRefreshBackground: () -> Unit,
    val onAttach: (String) -> Unit,
    val onOpenAgent: (String, SubagentSnapshot) -> Unit,
    val onToggleFinished: (String) -> Unit,
)

/** Collapsible right rail: live sessions with nested subagents, then background agents. [background] is null while loading. */
@Composable
fun AgentsPanel(
    sessions: List<SessionSnapshot>,
    teams: Map<String, Team>,
    selectedSessionId: String?,
    finishedOpen: (String) -> Boolean,
    now: () -> Instant,
    background: List<BackgroundAgent>?,
    actions: AgentsPanelActions,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    Row(modifier.fillMaxHeight()) {
        VerticalRule()
        Box(Modifier.width(agentsPanelWidth).fillMaxHeight().background(colors.sidebar)) {
            LazyColumn(Modifier.fillMaxHeight(), list, contentPadding = PaddingValues(Spacing.md + 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item(key = "agents-header") { PanelTitle("Agents", sessions.size) }
                if (sessions.isEmpty()) item(key = "agents-empty") { Label("No live sessions yet. Launch Claude to start one.", color = colors.textTertiary, style = Type.small, maxLines = 3) }
                items(sessions, key = { it.ccmSessionId }) { session ->
                    SessionCard(session, teams[session.ccmSessionId], session.ccmSessionId == selectedSessionId, finishedOpen(session.ccmSessionId), now, actions, Modifier.animateItem())
                }
                item(key = "background-header") { PanelTitle("Background agents", background?.size, Modifier.padding(top = Spacing.md), actions.onRefreshBackground) }
                backgroundItems(background, actions.onAttach)
            }
            ThinScrollbar(list)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.backgroundItems(background: List<BackgroundAgent>?, onAttach: (String) -> Unit) {
    when {
        background == null -> items(SKELETON_ROWS, key = { "skeleton-$it" }) { SkeletonBlock(Modifier.fillMaxWidth().height(skeletonRowHeight), RoundedCornerShape(Radius.md)) }
        background.isEmpty() -> item(key = "background-empty") { Label("Nothing running in the background.", color = colors.textTertiary, style = Type.small) }
        else -> items(background, key = { "bg-${it.id}" }) { agent -> BackgroundAgentCard(agent) { onAttach(agent.id) } }
    }
}

@Composable
private fun PanelTitle(title: String, count: Int?, modifier: Modifier = Modifier, onRefresh: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(title, color = colors.textPrimary, style = Type.heading)
        count?.let { CountBadge(it) }
        Box(Modifier.weight(1f))
        onRefresh?.let { IconButton(Icons.Refresh, it, tooltip = "Refresh", size = 24.dp) }
    }
}

@Composable
private fun SessionCard(session: SessionSnapshot, team: Team?, selected: Boolean, finishedOpen: Boolean, now: () -> Instant, actions: AgentsPanelActions, modifier: Modifier) {
    val shape = RoundedCornerShape(Radius.lg)
    val tint = colors.status(session.status)
    val edge = when {
        selected -> colors.accent.copy(alpha = Alpha.GLOW)
        session.status == AgentStatus.NEEDS_ACTION -> tint.copy(alpha = Alpha.BORDER_TINT)
        else -> colors.border
    }
    Pressable({ actions.onOpenSession(session.ccmSessionId) }, modifier.fillMaxWidth().border(1.dp, edge, shape), shape, if (selected) colors.surfaceSelected else colors.surface, if (selected) colors.surfaceSelected else colors.surfaceHover) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusDot(session.status, Modifier.padding(horizontal = Spacing.xs))
                Label(sessionTitle(session), Modifier.weight(1f), style = Type.bodyMedium, tooltipWhenTruncated = true)
                permissionModeLabel(session.permissionMode)?.let { Chip(it, modeTint(session.permissionMode)) }
            }
            MetaLine(session, now)
            session.activity?.let { Label(it, color = colors.textSecondary, style = Type.small, maxLines = 2) }
            session.error?.let { Label(it, color = colors.danger, style = Type.small, maxLines = 2) }
            if (session.backgroundTasks > 0) Chip(backgroundTasksLabel(session.backgroundTasks), colors.accent)
            if (session.subagents.isNotEmpty()) {
                val subagentActions = SubagentActions({ actions.onOpenAgent(session.ccmSessionId, it) }) { actions.onToggleFinished(session.ccmSessionId) }
                SubagentList(session, team, now, finishedOpen, subagentActions)
            }
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
    return listOfNotNull(session.model, age).joinToString("   ")
}

@Composable
private fun BackgroundAgentCard(agent: BackgroundAgent, onAttach: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.border, shape).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(6.dp)) { StatusDot(AgentStatus.WORKING, size = 6.dp) }
        Column(Modifier.weight(1f)) {
            Label(agent.name.ifBlank { agent.id }, style = Type.bodyMedium, tooltipWhenTruncated = true)
            Label(listOfNotNull(agent.status, agent.summary).joinToString("   "), color = colors.textTertiary, style = Type.caption, maxLines = 2)
        }
        AppButton("Attach", onAttach, compact = true)
    }
}

/** Own composable so only this line recomposes on each clock tick. */
@Composable
private fun MetaLine(session: SessionSnapshot, now: () -> Instant) {
    Label(metaLine(session, now()), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
}
