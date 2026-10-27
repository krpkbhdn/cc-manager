package com.github.krpkbhdn.ccmanager.ui.sessions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.formatCost
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val ACTIVITY_LINES = 3
private val footerHeight = 28.dp

/** One session at a glance: project, title, status, last action, time since the last event, context and cost. Every card has the same height. */
@Composable
fun SessionCard(
    session: SessionSnapshot,
    projectName: String,
    usage: SessionUsage?,
    now: Instant,
    stale: Boolean,
    proposalHeadline: String?,
    onOpen: () -> Unit,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.lg)
    val waiting = session.status == AgentStatus.NEEDS_ACTION
    val accent = colors.danger
    val base = if (waiting) accent.copy(alpha = Alpha.FAINT).compositeOver(colors.surface) else colors.surface
    val hover = if (waiting) accent.copy(alpha = Alpha.SOFT).compositeOver(colors.surfaceHover) else colors.surfaceHover
    val frame = if (waiting) Modifier.border(1.dp, accent.copy(alpha = Alpha.BORDER_TINT), shape) else Modifier
    Pressable(onOpen, modifier.card(shape).then(frame), shape = shape, base = base, hover = hover) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                ProjectAvatar(projectName, size = 36.dp, status = session.status, cutout = colors.surface)
                Column(Modifier.weight(1f)) {
                    Label(sessionTitle(session), style = Type.subtitle, tooltipWhenTruncated = true)
                    Label(projectName, color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
                }
                StatusChip(session.status)
            }
            Label(proposalHeadline ?: session.activity.orEmpty(), color = colors.textSecondary, style = Type.body, maxLines = ACTIVITY_LINES, minLines = ACTIVITY_LINES)
            Row(Modifier.height(footerHeight), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    silence(session, now, stale)
                    figures(usage).forEach { Label(it, color = colors.textTertiary, style = Type.numeric) }
                }
                if (waiting) AppButton("Answer", onAnswer, variant = ButtonVariant.PRIMARY, icon = Icons.Message, compact = true)
            }
        }
    }
}

@Composable
private fun silence(session: SessionSnapshot, now: Instant, stale: Boolean) {
    val elapsed = session.lastEventAt?.let { formatElapsed(Duration.between(it, now)) } ?: return
    if (stale) StaleMarker("No activity for $elapsed") else Label("$elapsed ago", color = colors.textTertiary, style = Type.numeric)
}

private fun figures(usage: SessionUsage?): List<String> = listOfNotNull(
    usage?.contextUsedPercent()?.let { "ctx ${it.roundToInt()}%" },
    usage?.costUsd()?.let(::formatCost),
)
