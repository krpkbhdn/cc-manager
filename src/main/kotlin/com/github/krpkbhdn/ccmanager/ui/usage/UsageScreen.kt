package com.github.krpkbhdn.ccmanager.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.UsageWindowDetail
import com.github.krpkbhdn.ccmanager.ui.state.formatCost
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.usage.history.SessionCost

private val tileMinWidth = 160.dp
private val legendDot = 8.dp
private const val SESSION_ID_PREFIX = 8
private val periods = UsagePeriod.entries.map { Segment(it, it.label) }

/** Cost history: summary tiles, stacked daily bars per project, cost per model and the most expensive sessions of the chosen period. */
@Composable
fun UsageScreen(state: UsageState, limits: List<UsageWindowDetail>, projectName: (Long) -> String, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { state.refresh() }
    val data = state.data
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("Usage history", Modifier.weight(1f), style = Type.title)
            SegmentedControl(periods, state.period, state::select)
        }
        when {
            data == null -> Label("Loading", color = colors.textTertiary)
            data.empty -> EmptyState(Icons.Gauge, "No usage recorded yet", "Costs appear here after a session reports its first reply.", Modifier.padding(top = Spacing.huge))
            else -> UsageBody(data, limits, projectName)
        }
    }
}

@Composable
private fun UsageBody(data: UsageData, limits: List<UsageWindowDetail>, projectName: (Long) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        summaryTiles(data, limits).forEach { Tile(it.title, it.value) }
    }
    Section("Cost per day") {
        DailyBarsChart(data.days, projectName)
        Legend(data.projects, projectName)
    }
    Section("Cost per model") { ModelBars(data.models) }
    Section("Most expensive sessions") {
        if (data.sessions.isEmpty()) Label("No sessions in this period", color = colors.textTertiary, style = Type.small)
        data.sessions.forEach { SessionRow(it, projectName(it.projectId())) }
    }
}

@Composable
private fun Tile(title: String, value: String) {
    Column(Modifier.widthIn(min = tileMinWidth).card().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(title, color = colors.textSecondary, style = Type.small)
        Label(value, style = Type.title)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label(title, style = Type.heading)
        content()
    }
}

@Composable
private fun Legend(projects: List<ProjectSlice>, projectName: (Long) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        projects.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                Box(Modifier.size(legendDot).background(projectColor(slice.projectId), CircleShape))
                Label(slice.projectId?.let(projectName) ?: "Other", style = Type.small)
                Label(formatCost(slice.costUsd), color = colors.textSecondary, style = Type.numericCaption)
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionCost, project: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.weight(1f)) {
            Label(project, style = Type.bodyMedium, tooltipWhenTruncated = true)
            val id = (session.claudeSessionId() ?: session.ccmSessionId()).orEmpty().take(SESSION_ID_PREFIX)
            Label(listOf(session.model().ifBlank { "Unknown model" }, id).filter { it.isNotEmpty() }.joinToString("  ·  "), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
        }
        Label(formatCost(session.costUsd()), color = colors.textSecondary, style = Type.numeric)
    }
}
