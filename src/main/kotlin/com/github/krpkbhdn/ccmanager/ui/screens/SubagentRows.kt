package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentState
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.activeSubagents
import com.github.krpkbhdn.ccmanager.ui.state.dotStatus
import com.github.krpkbhdn.ccmanager.ui.state.elapsed
import com.github.krpkbhdn.ccmanager.ui.state.finalLabel
import com.github.krpkbhdn.ccmanager.ui.state.finishedSubagents
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.state.liveLine
import com.github.krpkbhdn.ccmanager.ui.state.statsLine
import com.github.krpkbhdn.ccmanager.ui.state.summaryLine
import com.github.krpkbhdn.ccmanager.ui.teams.RoleAvatar
import com.github.krpkbhdn.ccmanager.ui.teams.RoleChip
import com.github.krpkbhdn.ccmanager.ui.teams.slotOf
import com.github.krpkbhdn.ccmanager.ui.teams.subagentRole
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant

private val avatarSize = 18.dp
private val statusColumn = 16.dp
private val rowPadding = Spacing.sm
private const val ACTIVITY_LINES = 2
private const val SUMMARY_LINES = 3

class SubagentActions(val onOpen: (SubagentSnapshot) -> Unit, val onToggleFinished: () -> Unit)

/** Live subagents of a session as rows, then the collapsible list of the closed ones. */
@Composable
fun SubagentList(session: SessionSnapshot, team: Team?, now: () -> Instant, finishedOpen: Boolean, actions: SubagentActions) {
    val finished = session.finishedSubagents()
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        session.activeSubagents().forEach { agent ->
            val role = team?.subagentRole(agent.agentType)
            ActiveSubagentRow(agent, role, role?.let { team.slotOf(it) } ?: 0, now) { actions.onOpen(agent) }
        }
        if (finished.isNotEmpty()) {
            FinishedHeader(finished.size, finishedOpen, actions.onToggleFinished)
            AnimatedVisibility(finishedOpen, enter = expandVertically(Motion.base()) + fadeIn(Motion.base()), exit = shrinkVertically(Motion.fast()) + fadeOut(Motion.fast())) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    finished.forEach { agent -> FinishedSubagentRow(agent) { actions.onOpen(agent) } }
                }
            }
        }
    }
}

@Composable
private fun ActiveSubagentRow(agent: SubagentSnapshot, role: TeamRole?, slot: Int, now: () -> Instant, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    val waiting = agent.state == SubagentState.NEEDS_ACTION
    val edge = if (waiting) colors.danger.copy(alpha = Alpha.BORDER_TINT) else Color.Transparent
    Pressable(onClick, Modifier.fillMaxWidth().alpha(if (agent.stale) Alpha.DISABLED else 1f).border(1.dp, edge, shape), shape, colors.hoverOverlay, colors.surfaceHover) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = rowPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.padding(horizontal = Spacing.xs)) { StatusDot(agent.dotStatus()) }
                if (role != null) RoleAvatar(role.name(), slot, avatarSize)
                Label(agent.agentType.ifBlank { "agent" }, Modifier.weight(1f), style = Type.smallMedium, tooltipWhenTruncated = true)
                ElapsedLabel(agent, now)
            }
            LiveLine(agent, now, waiting, Modifier.padding(start = statusColumn))
            Row(Modifier.padding(start = statusColumn), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(agent.statsLine(), Modifier.weight(1f, fill = false), color = colors.textTertiary, style = Type.caption)
                role?.let { RoleChip(it, slot) }
            }
        }
    }
}

@Composable
private fun FinishedHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    Pressable(onToggle, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(if (open) Icons.ChevronDown else Icons.ChevronRight, colors.textTertiary, size = IconSize.medium)
            Label("Finished", color = colors.textSecondary, style = Type.smallMedium)
            CountBadge(count)
        }
    }
}

@Composable
private fun FinishedSubagentRow(agent: SubagentSnapshot, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    val (icon, tint) = finalGlyph(agent.state)
    Pressable(onClick, Modifier.fillMaxWidth(), shape, colors.hoverOverlay, colors.surfaceHover) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = rowPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.padding(horizontal = Spacing.xs)) { Glyph(icon, tint, size = IconSize.medium) }
                Label(agent.agentType.ifBlank { "agent" }, Modifier.weight(1f), color = colors.textSecondary, style = Type.smallMedium, tooltipWhenTruncated = true)
                Label("${agent.state.finalLabel()} · ${formatElapsed(agent.elapsed(agent.endedAt ?: agent.startedAt))}", color = tint, style = Type.captionMedium)
            }
            Label(agent.summaryLine(), Modifier.padding(start = statusColumn), color = colors.textTertiary, style = Type.caption, maxLines = SUMMARY_LINES, tooltipWhenTruncated = true)
            Row(Modifier.padding(start = statusColumn), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Glyph(Icons.Log, colors.accent, size = IconSize.small)
                Label("Open log", color = colors.accent, style = Type.captionMedium)
            }
        }
    }
}

@Composable
private fun finalGlyph(state: SubagentState) = when (state) {
    SubagentState.FAILED -> Icons.CircleAlert to colors.danger
    SubagentState.INTERRUPTED -> Icons.Stop to colors.warning
    else -> Icons.CircleCheck to colors.status(AgentStatus.IDLE)
}

/** Own composable so only this text recomposes on each clock tick. */
@Composable
private fun ElapsedLabel(agent: SubagentSnapshot, now: () -> Instant) {
    Label(formatElapsed(agent.elapsed(now())), color = colors.textTertiary, style = Type.numericCaption)
}

@Composable
private fun LiveLine(agent: SubagentSnapshot, now: () -> Instant, waiting: Boolean, modifier: Modifier) {
    Label(
        agent.liveLine(now()),
        modifier,
        color = if (waiting) colors.danger else colors.textSecondary,
        style = Type.small,
        maxLines = ACTIVITY_LINES,
        tooltipWhenTruncated = true,
    )
}
