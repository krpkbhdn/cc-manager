package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry.Kind
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.state.AgentLog
import com.github.krpkbhdn.ccmanager.ui.state.AgentsState
import com.github.krpkbhdn.ccmanager.ui.state.elapsed
import com.github.krpkbhdn.ccmanager.ui.state.finalLabel
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant
import kotlinx.coroutines.delay

private val dialogWidth = 720.dp
private val logMinHeight = 96.dp
private val logMaxHeight = 460.dp
private const val ENTRY_LINES = 12
private const val LIVE_REFRESH_MILLIS = 2_000L

class SubagentLogActions(val onReload: () -> Unit, val onShowInTerminal: () -> Unit, val onClose: () -> Unit)

/** Read-only transcript of one subagent, re-read every couple of seconds while the agent is running. */
@Composable
fun SubagentLogHost(agents: AgentsState, now: () -> Instant, log: AgentLog) {
    val agent = agents.current(log)
    LaunchedEffect(agent.agentId, agent.running()) {
        while (agent.running()) {
            delay(LIVE_REFRESH_MILLIS)
            agents.reload()
        }
    }
    val actions = SubagentLogActions(agents::reload, { agents.showSessionInTerminal(log.ccmSessionId) }, agents::closeLog)
    SubagentLogDialog(agent, log.entries, now, actions)
}

/** [entries] is null while the transcript loads. */
@Composable
fun SubagentLogDialog(agent: SubagentSnapshot, entries: List<TranscriptEntry>?, now: () -> Instant, actions: SubagentLogActions) {
    ModalHost(actions.onClose, width = dialogWidth) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LogHeader(agent, now)
            Box(Modifier.fillMaxWidth().heightIn(min = logMinHeight, max = logMaxHeight).background(colors.terminal, RoundedCornerShape(Radius.md))) { LogBody(entries) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                AppButton("Reload", actions.onReload)
                AppButton("Show session in terminal", actions.onShowInTerminal)
                AppButton("Close", actions.onClose)
            }
        }
    }
}

@Composable
private fun LogHeader(agent: SubagentSnapshot, now: () -> Instant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(agent.agentType.ifBlank { "agent" }, Modifier.weight(1f, fill = false), style = Type.subtitle)
        Label("${agent.state.finalLabel()} · ${formatElapsed(agent.elapsed(now()))} · ${agent.toolCalls} tool calls", color = colors.textTertiary, style = Type.small)
        if (agent.running() && !agent.stale) {
            StatusDot(AgentStatus.WORKING, size = 6.dp)
            Label("Live", color = colors.warning, style = Type.captionMedium)
        }
    }
}

@Composable
private fun LogBody(entries: List<TranscriptEntry>?) {
    when {
        entries == null -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(40.dp)) }
        }
        entries.isEmpty() -> Label("The transcript is not available yet. Claude writes it a moment after the agent starts or stops.", Modifier.padding(Spacing.lg), color = colors.textTertiary, maxLines = 3)
        else -> EntryList(entries)
    }
}

/** Wraps its content up to the cap; follows the end of the log while it grows, unless the user scrolled up. */
@Composable
private fun EntryList(entries: List<TranscriptEntry>) {
    val scroll = rememberScrollState()
    var pinned by remember { mutableStateOf(true) }
    LaunchedEffect(scroll) { snapshotFlow { scroll.value >= scroll.maxValue }.collect { pinned = it } }
    LaunchedEffect(entries.size, scroll.maxValue) { if (pinned) scroll.scrollTo(scroll.maxValue) }
    Box {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            entries.forEach { LogEntry(it) }
        }
        if (scroll.maxValue > 0) ThinScrollbar(scroll)
    }
}

@Composable
private fun LogEntry(entry: TranscriptEntry) {
    val tint = when (entry.kind) {
        Kind.PROMPT -> colors.accent
        Kind.ASSISTANT -> colors.success
        Kind.TOOL_CALL -> colors.warning
        Kind.TOOL_RESULT -> colors.textTertiary
    }
    val mono = entry.kind == Kind.TOOL_CALL || entry.kind == Kind.TOOL_RESULT
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Label(entry.title, color = tint, style = Type.captionMedium)
        Label(entry.text, color = if (mono) colors.textSecondary else colors.textPrimary, style = if (mono) Type.monoSmall else Type.small, maxLines = ENTRY_LINES, tooltipWhenTruncated = true)
    }
}
