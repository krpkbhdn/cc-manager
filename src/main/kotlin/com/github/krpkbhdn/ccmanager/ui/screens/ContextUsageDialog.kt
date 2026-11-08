package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.chrome.ContextBar
import com.github.krpkbhdn.ccmanager.ui.chrome.levelColor
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.BreakdownLoad
import com.github.krpkbhdn.ccmanager.ui.state.ContextPart
import com.github.krpkbhdn.ccmanager.ui.state.ContextSegment
import com.github.krpkbhdn.ccmanager.ui.state.ContextUsageView
import com.github.krpkbhdn.ccmanager.ui.state.UsageLevel
import com.github.krpkbhdn.ccmanager.ui.state.compactTokens
import com.github.krpkbhdn.ccmanager.ui.state.formatElapsed
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private val dialogWidth = 1000.dp
private val contentHeight = 560.dp
private val dialogChromeHeight = 230.dp
private val tabHeight = 32.dp
private val tabUnderline = 2.dp
private val shareWidth = 40.dp
private const val MIN_WEIGHT = 0.0001f
private const val PERCENT = 100
private const val COMPACT_ADVICE = "Context is filling up. Run /compact to summarise the conversation, or start a new session."
private const val NO_BREAKDOWN = "Token breakdown appears after the next response"
private const val NO_DETAILS = "Per-item details appear once claude /context has been measured. Press Refresh to measure now."
private const val MEASURING = "Measuring what fills the context with claude /context..."

/** Details of the selected session's context window; a modal so the terminal never resizes. */
@Composable
internal fun ContextUsageDialog(view: ContextUsageView?, load: BreakdownLoad?, loading: Boolean, now: Instant, onRefresh: () -> Unit, showDetails: Boolean, onSelectDetails: (Boolean) -> Unit, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = minOf(dialogWidth, maxWidth - Spacing.lg * 2)
        val height = minOf(contentHeight, maxHeight - dialogChromeHeight)
        ContextUsageModal(width, height, view, load, loading, now, onRefresh, showDetails, onSelectDetails, onDismiss)
    }
}

@Composable
private fun ContextUsageModal(width: Dp, contentHeight: Dp, view: ContextUsageView?, load: BreakdownLoad?, loading: Boolean, now: Instant, onRefresh: () -> Unit, showDetails: Boolean, onSelectDetails: (Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalHost(onDismiss, width = width) {
        val actions = @Composable {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(Spacing.sm, Alignment.End)) {
                val raw = (load as? BreakdownLoad.Done)?.breakdown?.raw().orEmpty()
                AppButton("Copy raw output", { copyToClipboard(raw) }, icon = Icons.Copy, enabled = raw.isNotBlank())
                AppButton("Refresh", onRefresh, icon = Icons.Refresh, enabled = !loading, loading = loading)
                AppButton("Close", onDismiss)
            }
        }
        DialogBody("Context usage", Icons.Gauge, colors.accent, actions = actions) {
            Column(Modifier.fillMaxWidth().height(contentHeight), verticalArrangement = spacedBy(Spacing.md)) {
                ViewToggle(showDetails, onSelectDetails)
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = spacedBy(Spacing.lg)) {
                    if (showDetails) DetailsTab(load) else SummaryTab(view, load, now)
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(view: ContextUsageView?, load: BreakdownLoad?, now: Instant) {
    if (view == null) {
        Label("No context data yet. It appears after the session's first response.", color = colors.textSecondary, maxLines = WRAP)
        return
    }
    Summary(view, now)
    if (view.level != UsageLevel.NORMAL) Label(COMPACT_ADVICE, color = levelColor(view.level), style = Type.bodyMedium, maxLines = WRAP)
    if (load is BreakdownLoad.Done) BreakdownSummary(load.breakdown) else StatusLineBreakdown(view, load)
    Totals(view)
}

@Composable
private fun DetailsTab(load: BreakdownLoad?) {
    when (load) {
        is BreakdownLoad.Done -> BreakdownDetails(load.breakdown)
        BreakdownLoad.Loading -> Label(MEASURING, color = colors.textSecondary, style = Type.body, maxLines = WRAP)
        is BreakdownLoad.Failed -> Label("Breakdown unavailable: ${load.reason}", color = colors.warning, style = Type.body, maxLines = WRAP)
        else -> Label(NO_DETAILS, color = colors.textSecondary, style = Type.body, maxLines = WRAP)
    }
}

@Composable
private fun ViewToggle(showDetails: Boolean, onSelect: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().wrapContentHeight(Alignment.Bottom).height(1.dp).background(colors.border))
        Row(horizontalArrangement = spacedBy(Spacing.xs)) {
            ViewTab("Summary", !showDetails) { onSelect(false) }
            ViewTab("Details", showDetails) { onSelect(true) }
        }
    }
}

@Composable
private fun ViewTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick, Modifier.height(tabHeight), RoundedCornerShape(Radius.sm), role = Role.Tab) {
        Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Label(label, color = if (selected) colors.textPrimary else colors.textSecondary, style = if (selected) Type.smallMedium else Type.small)
            }
            Box(Modifier.fillMaxWidth().height(tabUnderline).background(if (selected) colors.accent else Color.Transparent))
        }
    }
}

@Composable
private fun StatusLineBreakdown(view: ContextUsageView, load: BreakdownLoad?) {
    Column(verticalArrangement = spacedBy(Spacing.md)) {
        when (load) {
            BreakdownLoad.Loading -> Label(MEASURING, color = colors.textSecondary, style = Type.caption, maxLines = WRAP)
            is BreakdownLoad.Failed -> Label("Breakdown unavailable: ${load.reason}", color = colors.warning, style = Type.caption, maxLines = WRAP)
            else -> Unit
        }
        if (view.segments.isEmpty()) Label(NO_BREAKDOWN, color = colors.textTertiary, style = Type.caption, maxLines = WRAP) else Breakdown(view.segments)
    }
}

@Composable
private fun Summary(view: ContextUsageView, now: Instant) {
    Column(verticalArrangement = spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = spacedBy(Spacing.md), verticalAlignment = Alignment.Bottom) {
            Label(view.percentText, color = levelColor(view.level), style = Type.title)
            view.tokensText?.let { Label("$it tokens", color = colors.textSecondary, style = Type.body) }
        }
        ContextBar(view.fraction, view.level, Modifier.fillMaxWidth().height(barHeight))
        val updated = "updated ${formatElapsed(Duration.between(view.receivedAt, now).coerceAtLeast(Duration.ZERO))} ago"
        Label(listOfNotNull(view.model, updated).joinToString(" · "), color = colors.textTertiary, style = Type.caption, maxLines = WRAP)
    }
}

@Composable
private fun Breakdown(segments: List<ContextSegment>) {
    Column(verticalArrangement = spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth().height(barHeight).background(colors.border, RoundedCornerShape(Radius.pill)), horizontalArrangement = spacedBy(1.dp)) {
            segments.filter { it.fraction > 0f && it.part != ContextPart.FREE }.forEach { Box(Modifier.weight(it.fraction).height(barHeight).background(partColor(it.part))) }
            Box(Modifier.weight(segments.first { it.part == ContextPart.FREE }.fraction.coerceAtLeast(MIN_WEIGHT)))
        }
        segments.forEach { SegmentRow(it) }
    }
}

@Composable
private fun SegmentRow(segment: ContextSegment) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = spacedBy(Spacing.sm)) {
        Box(Modifier.size(legendDot).background(partColor(segment.part), RoundedCornerShape(Radius.pill)))
        Label(segment.part.label, Modifier.weight(1f), style = Type.body)
        Label(compactTokens(segment.tokens), color = colors.textSecondary, style = Type.numeric)
        Label("${(segment.fraction * PERCENT).roundToInt()}%", Modifier.width(shareWidth), color = colors.textTertiary, style = Type.numeric)
    }
}

@Composable
private fun Totals(view: ContextUsageView) {
    Column(verticalArrangement = spacedBy(Spacing.xs)) {
        view.outputTokens?.let { Figure("Last response output", compactTokens(it)) }
        if (view.totalInputTokens != null || view.totalOutputTokens != null) {
            Figure("Session total in / out", "${view.totalInputTokens?.let(::compactTokens) ?: "--"} / ${view.totalOutputTokens?.let(::compactTokens) ?: "--"}")
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Row(horizontalArrangement = spacedBy(Spacing.sm)) {
        Label(label, Modifier.weight(1f), color = colors.textSecondary, style = Type.body, maxLines = WRAP)
        Label(value, style = Type.numeric, maxLines = WRAP)
    }
}

@Composable
private fun partColor(part: ContextPart): Color = when (part) {
    ContextPart.CACHE_READ -> colors.accent
    ContextPart.CACHE_WRITE -> colors.accent.copy(alpha = Alpha.BORDER_TINT)
    ContextPart.INPUT -> colors.textSecondary
    ContextPart.FREE -> colors.border
}

