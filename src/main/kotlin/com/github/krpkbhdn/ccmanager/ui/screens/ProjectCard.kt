package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.ContextMenuArea
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.git.GitSummaryLine
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.byUrgency
import com.github.krpkbhdn.ccmanager.ui.state.lastActivityAt
import com.github.krpkbhdn.ccmanager.ui.state.mostUrgent
import com.github.krpkbhdn.ccmanager.ui.state.pathTail
import com.github.krpkbhdn.ccmanager.ui.state.relativeTime
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant

private const val MAX_PILLS = 3
private const val ACTIVITY_LINES = 2
private val cardMinHeight = 168.dp
private val menuButtonSize = 24.dp
private val gitLineHeight = 16.dp
private val footerHeight = 28.dp

/** Overview card of one project: identity, live activity line, session pills and last activity. */
@Composable
fun ProjectCard(project: Project, status: AgentStatus, sessions: List<SessionSnapshot>, git: GitSummary?, now: () -> Instant, actions: ProjectActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.xl)
    val urgent = mostUrgent(sessions)
    val lastActive = lastActivityAt(sessions)
    val edge = if (status == AgentStatus.OFFLINE) colors.border else colors.status(status).copy(alpha = Alpha.BORDER_TINT / 2)
    ContextMenuArea({ projectMenu(actions) }, modifier.fillMaxWidth()) {
        Pressable(actions.onOpen, Modifier.fillMaxWidth().heightIn(min = cardMinHeight).border(1.dp, edge, shape), shape, colors.surface, colors.surfaceHover) { hovered ->
            Column(Modifier.fillMaxWidth().padding(Spacing.lg + 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProjectAvatar(project.name, size = 40.dp, cutout = colors.surface)
                    Column(Modifier.weight(1f)) {
                        Label(project.name, style = Type.subtitle, tooltipWhenTruncated = true)
                        Label(pathTail(project.path, 3), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
                    }
                    StatusChip(status)
                    CardMenu(actions, hovered)
                }
                Box(Modifier.height(gitLineHeight)) { git?.let { GitSummaryLine(it) } }
                Label(
                    urgent?.let { it.activity ?: sessionTitle(it) } ?: "No live sessions. Launch Claude to start one.",
                    color = if (urgent == null) colors.textTertiary else colors.textSecondary,
                    style = Type.body,
                    maxLines = ACTIVITY_LINES,
                    minLines = ACTIVITY_LINES,
                )
                CardFooter(sessions, lastActive, now, hovered)
            }
        }
    }
}

@Composable
private fun CardMenu(actions: ProjectActions, hovered: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.size(menuButtonSize)) {
        if (hovered || menuOpen) IconButton(Icons.More, { menuOpen = true }, tooltip = "Project actions", size = menuButtonSize)
        if (menuOpen) PopupMenu(projectMenu(actions), { menuOpen = false }, alignEnd = true)
    }
}

@Composable
private fun CardFooter(sessions: List<SessionSnapshot>, lastActive: Instant?, now: () -> Instant, hovered: Boolean) {
    val arrow by animateFloatAsState(if (hovered) 1f else 0f, Motion.base(), label = "cardArrow")
    Row(Modifier.fillMaxWidth().height(footerHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            byUrgency(sessions).take(MAX_PILLS).forEach { SessionPill(it) }
            if (sessions.size > MAX_PILLS) Label("+${sessions.size - MAX_PILLS}", color = colors.textTertiary, style = Type.captionMedium, modifier = Modifier.padding(top = 3.dp))
        }
        lastActive?.let {
            Glyph(Icons.Clock, colors.textTertiary, size = IconSize.small)
            Label(relativeTime(it, now()), color = colors.textTertiary, style = Type.numericCaption)
        }
        Box(Modifier.alpha(arrow)) { Glyph(Icons.ArrowRight, colors.accent, size = IconSize.medium) }
    }
}

@Composable
private fun SessionPill(session: SessionSnapshot) {
    val tint = colors.status(session.status)
    Row(
        Modifier.clip(RoundedCornerShape(Radius.pill)).background(colors.hoverOverlay).padding(start = Spacing.sm, end = Spacing.sm + 2.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
    ) {
        StatusDot(session.status, size = 6.dp)
        Label(sessionTitle(session), Modifier.padding(0.dp), color = if (session.status == AgentStatus.NEEDS_ACTION) tint else colors.textSecondary, style = Type.captionMedium)
    }
}
