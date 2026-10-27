package com.github.krpkbhdn.ccmanager.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val ACTIVITY_LINES = 2

/** One session at a glance: project, title, status, last action, time since the last event, context and cost. */
@Composable
fun SessionCard(
    session: SessionSnapshot,
    projectName: String,
    usage: SessionUsage?,
    now: () -> Instant,
    proposalHeadline: String?,
    onOpen: () -> Unit,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Pressable(onOpen, modifier.card(shape), shape = shape, base = colors.surface, hover = colors.surfaceHover) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                ProjectAvatar(projectName, size = 36.dp, status = session.status, cutout = colors.surface)
                Column(Modifier.weight(1f)) {
                    Label(sessionTitle(session), style = Type.subtitle, tooltipWhenTruncated = true)
                    Label(projectName, color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
                }
                StatusChip(session.status)
            }
            (proposalHeadline ?: session.activity)?.let { Label(it, color = colors.textSecondary, style = Type.body, maxLines = ACTIVITY_LINES) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    figures(session, usage, now()).forEach { Label(it, color = colors.textTertiary, style = Type.numeric) }
                }
                if (session.status == AgentStatus.NEEDS_ACTION) AppButton("Answer", onAnswer, variant = ButtonVariant.PRIMARY, icon = Icons.Message, compact = true)
            }
        }
    }
}

private fun figures(session: SessionSnapshot, usage: SessionUsage?, now: Instant): List<String> = listOfNotNull(
    session.lastEventAt?.let { formatElapsed(Duration.between(it, now)) + " ago" },
    usage?.contextUsedPercent()?.let { "ctx ${it.roundToInt()}%" },
    usage?.costUsd()?.let(::formatCost),
)
