package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pill
import com.github.krpkbhdn.ccmanager.ui.state.ActivityTone
import com.github.krpkbhdn.ccmanager.ui.state.activityTone
import com.github.krpkbhdn.ccmanager.ui.state.eventLabel
import com.github.krpkbhdn.ccmanager.ui.state.formatClock
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val clockWidth = 64.dp
private val typeWidth = 150.dp
private val subagentSlotWidth = 76.dp

@Composable
private fun toneColor(tone: ActivityTone): Color = when (tone) {
    ActivityTone.NEUTRAL -> colors.textSecondary
    ActivityTone.ACCENT -> colors.accent
    ActivityTone.SUCCESS -> colors.success
    ActivityTone.DANGER -> colors.danger
}

/** Live log of one session's hook events, scrolled to the newest entry. */
@Composable
fun ActivityTab(entries: List<ActivityEntry>, hasSession: Boolean, modifier: Modifier = Modifier) {
    if (!hasSession) {
        EmptyState(AppIcon.Activity, "No session selected", "Launch Claude or pick a session on the left to follow what it is doing.", modifier)
        return
    }
    if (entries.isEmpty()) {
        EmptyState(AppIcon.Activity, "No activity yet", "Tool calls, prompts and agent events appear here as they happen.", modifier)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) { listState.scrollToItem(entries.lastIndex) }
    LazyColumn(modifier.fillMaxSize(), listState, contentPadding = PaddingValues(Spacing.md)) {
        items(entries) { entry -> ActivityRow(entry) }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label(formatClock(entry.at), Modifier.width(clockWidth), color = colors.textTertiary, size = FontSize.small, mono = true)
        Label(eventLabel(entry.type), Modifier.width(typeWidth), color = toneColor(activityTone(entry.type)), size = FontSize.small, mono = true)
        Box(Modifier.width(subagentSlotWidth)) { if (entry.agentId != null) Pill("subagent", colors.accent) }
        Label(entry.text, Modifier.weight(1f), size = FontSize.small, maxLines = 3)
    }
}
