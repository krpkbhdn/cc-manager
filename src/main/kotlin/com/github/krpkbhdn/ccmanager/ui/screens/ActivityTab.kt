package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ActivityTone
import com.github.krpkbhdn.ccmanager.ui.state.activityTone
import com.github.krpkbhdn.ccmanager.ui.state.eventLabel
import com.github.krpkbhdn.ccmanager.ui.state.formatClock
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val clockWidth = 68.dp
private val badgeSize = 24.dp
private val railWidth = 1.dp

@Composable
private fun toneColor(tone: ActivityTone): Color = when (tone) {
    ActivityTone.NEUTRAL -> colors.textTertiary
    ActivityTone.ACCENT -> colors.accent
    ActivityTone.SUCCESS -> colors.success
    ActivityTone.DANGER -> colors.danger
}

private fun eventIcon(type: HookEventType): ImageVector = when (type) {
    HookEventType.USER_PROMPT_SUBMIT, HookEventType.USER_PROMPT_EXPANSION -> Icons.Message
    HookEventType.PRE_TOOL_USE, HookEventType.POST_TOOL_USE, HookEventType.POST_TOOL_BATCH -> Icons.Terminal
    HookEventType.PERMISSION_REQUEST, HookEventType.ELICITATION -> Icons.Hand
    HookEventType.PERMISSION_DENIED, HookEventType.POST_TOOL_USE_FAILURE, HookEventType.STOP_FAILURE -> Icons.Alert
    HookEventType.STOP, HookEventType.TASK_COMPLETED -> Icons.Check
    HookEventType.SUBAGENT_START, HookEventType.SUBAGENT_STOP -> Icons.Bot
    HookEventType.SESSION_START, HookEventType.SESSION_END -> Icons.Play
    else -> Icons.Activity
}

/** Timeline of one session's hook events with coloured badges, scrolled to the newest entry. */
@Composable
fun ActivityTab(entries: List<ActivityEntry>, hasSession: Boolean, onLaunch: (LaunchMode) -> Unit, launching: Boolean, modifier: Modifier = Modifier) {
    if (!hasSession) {
        LaunchPad(onLaunch, launching, modifier)
        return
    }
    if (entries.isEmpty()) {
        EmptyState(Icons.Activity, "No activity yet", "Prompts, tool calls and agent events appear here as they happen.", modifier)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.lastOrNull()) { listState.scrollToItem(entries.lastIndex) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.lg)) {
            items(entries.size) { index -> TimelineRow(entries[index], isFirst = index == 0, isLast = index == entries.lastIndex) }
        }
        ThinScrollbar(listState)
    }
}

@Composable
private fun TimelineRow(entry: ActivityEntry, isFirst: Boolean, isLast: Boolean) {
    val tone = toneColor(activityTone(entry.type))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label(formatClock(entry.at), Modifier.width(clockWidth).padding(top = Spacing.xs), color = colors.textTertiary, style = Type.monoSmall)
        Column(Modifier.width(badgeSize).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(railWidth).height(Spacing.xs).background(if (isFirst) Color.Transparent else colors.border))
            Box(Modifier.size(badgeSize).clip(CircleShape).background(tone.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(eventIcon(entry.type), tone, size = IconSize.small)
            }
            Box(Modifier.width(railWidth).weight(1f).background(if (isLast) Color.Transparent else colors.border))
        }
        Column(Modifier.weight(1f).padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Label(eventLabel(entry.type), color = tone, style = Type.smallMedium)
                if (entry.agentId != null) Chip("subagent", colors.accent)
            }
            Label(entry.text, color = colors.textPrimary, style = Type.body, maxLines = 3)
        }
    }
}

/** Shown when a project has no session yet: start one right here. */
@Composable
private fun LaunchPad(onLaunch: (LaunchMode) -> Unit, launching: Boolean, modifier: Modifier) {
    EmptyState(Icons.Terminal, "No session in this project", "Launch Claude to work in this folder. Its terminal opens here and its activity is tracked on this screen.", modifier) {
        Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AppButton("Launch Claude", { onLaunch(LaunchMode.NEW) }, variant = ButtonVariant.PRIMARY, icon = Icons.Play, loading = launching)
            AppButton("Continue last", { onLaunch(LaunchMode.CONTINUE) }, icon = Icons.ArrowRight, enabled = !launching)
            AppButton("Resume", { onLaunch(LaunchMode.RESUME_PICKER) }, icon = Icons.Rewind, enabled = !launching)
        }
    }
}
