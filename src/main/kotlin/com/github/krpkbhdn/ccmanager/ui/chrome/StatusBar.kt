package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.install.LoginState
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

val statusBarHeight = 26.dp

/** Slim IntelliJ-style status bar: where you are on the left, live session counts and Claude's state on the right. */
@Composable
fun StatusBar(state: AppState, modifier: Modifier = Modifier) {
    Column(modifier.background(colors.sidebar)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().height(statusBarHeight).padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Breadcrumb(state, Modifier.weight(1f))
            SessionCounters(state)
            state.installation?.let { ClaudeSegment(it) }
        }
    }
}

@Composable
private fun Breadcrumb(state: AppState, modifier: Modifier) {
    val parts = when (val route = state.route) {
        Route.Overview -> listOf("Overview")
        Route.Teams -> listOf("Teams")
        is Route.Project -> listOf(state.projectName(route.id)) + listOfNotNull(state.viewOf(route.id).focusedSession?.let { state.live.find(it) }?.let(::sessionTitle))
    }
    Row(modifier.padding(start = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 1.dp)) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Glyph(Icons.ChevronRight, colors.textTertiary, size = IconSize.small)
            Label(part, Modifier.weight(1f, fill = false), color = if (index == parts.lastIndex) colors.textPrimary else colors.textSecondary, style = Type.caption, tooltipWhenTruncated = true)
        }
    }
}

@Composable
private fun SessionCounters(state: AppState) {
    val sessions = state.live.sessions
    val counts = listOf(AgentStatus.NEEDS_ACTION, AgentStatus.WORKING, AgentStatus.IDLE).map { status -> status to sessions.count { it.status == status } }.filter { it.second > 0 }
    if (counts.isEmpty()) return
    Pressable({ state.navigate(Route.Overview) }, shape = RoundedCornerShape(Radius.sm)) {
        Row(Modifier.padding(horizontal = Spacing.sm).height(statusBarHeight - 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            counts.forEach { (status, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                    StatusDot(status, size = 6.dp)
                    Label(count.toString(), color = colors.textSecondary, style = Type.numericCaption)
                }
            }
        }
    }
}

@Composable
private fun ClaudeSegment(installation: ClaudeInstallation) {
    val signedOut = installation.login == LoginState.LOGGED_OUT
    Row(Modifier.padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
        StatusDot(if (signedOut) AgentStatus.WORKING else AgentStatus.IDLE, size = 6.dp)
        Label(
            "Claude ${installation.version?.substringBefore(' ') ?: ""}".trim() + if (signedOut) " (signed out)" else "",
            color = colors.textSecondary,
            style = Type.caption,
        )
    }
}
