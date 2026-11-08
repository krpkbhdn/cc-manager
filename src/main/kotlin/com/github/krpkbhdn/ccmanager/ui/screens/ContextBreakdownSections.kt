package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.state.DEFERRED_MARK
import com.github.krpkbhdn.ccmanager.ui.state.compactTokens
import com.github.krpkbhdn.ccmanager.ui.state.contextHints
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown.Category
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val barHeight = 10.dp
internal val legendDot = 8.dp
private val shareWidth = 64.dp
private const val FREE_SPACE = "Free space"
private const val AUTOCOMPACT_BUFFER = "Autocompact buffer"
private const val MIN_WEIGHT = 0.0001f
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

internal val tokensWidth = 72.dp
internal const val WRAP = Int.MAX_VALUE

/** Short view of claude `/context`: where the window goes by category, what is still free, and advice for the heavy parts. */
@Composable
internal fun BreakdownSummary(breakdown: ContextBreakdown) {
    val (reserved, used) = breakdown.categories().partition { it.name() == FREE_SPACE || it.name() == AUTOCOMPACT_BUFFER }
    Column(verticalArrangement = spacedBy(Spacing.lg)) {
        Column(verticalArrangement = spacedBy(Spacing.sm)) {
            Label("What fills the context", style = Type.bodyMedium)
            val source = if (breakdown.approximateMessages()) "Approximate: a fresh Claude process (no conversation yet)" else "Measured by claude /context"
            Label("$source, at ${timeFormat.format(breakdown.measuredAt())}. MCP tools, system tools and skills reflect this folder's configuration; team agents, the team system prompt and MCP servers or plugins the app adds at launch are not included, so team sessions are under-reported.", color = colors.textTertiary, style = Type.caption, maxLines = WRAP)
        }
        CategoryBar(breakdown, used, reserved)
        Column(verticalArrangement = spacedBy(Spacing.sm)) {
            used.forEachIndexed { index, category -> CategoryRow(categoryColor(index, category.name()), category) }
            reserved.forEach { CategoryRow(colors.border, it) }
        }
        Hints(contextHints(breakdown))
    }
}

@Composable
private fun categoryColor(index: Int, name: String): Color = when (name) {
    FREE_SPACE, AUTOCOMPACT_BUFFER -> colors.border
    else -> listOf(colors.accent, colors.success, colors.warning, colors.danger, colors.textSecondary, colors.accent.copy(alpha = Alpha.BORDER_TINT))[index % 6]
}

@Composable
private fun CategoryBar(breakdown: ContextBreakdown, used: List<Category>, reserved: List<Category>) {
    val window = breakdown.windowTokens().coerceAtLeast(1)
    fun weight(tokens: Long) = (tokens.toFloat() / window).coerceAtLeast(MIN_WEIGHT)
    Row(Modifier.fillMaxWidth().height(barHeight).background(colors.border, RoundedCornerShape(Radius.pill)), horizontalArrangement = spacedBy(1.dp)) {
        used.forEachIndexed { index, category ->
            if (!category.name().contains(DEFERRED_MARK) && category.tokens() > 0) {
                Box(Modifier.weight(weight(category.tokens())).height(barHeight).background(categoryColor(index, category.name())))
            }
        }
        Box(Modifier.weight(weight(reserved.sumOf { it.tokens() })))
    }
}

@Composable
private fun CategoryRow(color: Color, category: Category) {
    val deferred = category.name().contains(DEFERRED_MARK)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = spacedBy(Spacing.sm)) {
        Box(Modifier.size(legendDot).background(color, RoundedCornerShape(Radius.pill)))
        Label(category.name().replace(DEFERRED_MARK, "").trim(), Modifier.weight(1f), style = Type.body, maxLines = WRAP)
        if (deferred) Label("loaded on use", color = colors.textTertiary, style = Type.caption, maxLines = WRAP)
        Label(compactTokens(category.tokens()), Modifier.width(tokensWidth), color = colors.textSecondary, style = Type.numeric)
        Label(String.format(Locale.ROOT, "%.1f%%", category.percent()), Modifier.width(shareWidth), color = colors.textTertiary, style = Type.numeric)
    }
}

@Composable
private fun Hints(hints: List<String>) {
    Column(verticalArrangement = spacedBy(Spacing.xs)) {
        Label("How to reduce it", style = Type.bodyMedium)
        if (hints.isEmpty()) Label("Nothing stands out; no part of the context is unusually heavy.", color = colors.textSecondary, style = Type.body, maxLines = WRAP)
        hints.forEach { Label("- $it", color = colors.textSecondary, style = Type.body, maxLines = WRAP) }
    }
}
