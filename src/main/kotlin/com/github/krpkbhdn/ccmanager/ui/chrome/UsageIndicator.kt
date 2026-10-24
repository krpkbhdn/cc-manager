package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.UsageLevel
import com.github.krpkbhdn.ccmanager.ui.state.UsageMeter
import com.github.krpkbhdn.ccmanager.ui.state.usageMeters
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val barWidth = 28.dp
private val barHeight = 4.dp
private val chipHeight = 28.dp

/** The top bar's usage chip with the detail panel anchored below it. */
@Composable
internal fun UsageIndicator(state: AppState) {
    Box {
        UsageChip(usageMeters(state.rateLimits, state.now), state.usageOpen) { state.usageOpen = !state.usageOpen }
        if (state.usageOpen) UsagePanel(state) { state.usageOpen = false }
    }
}

/** One small bar per limit window, muted text while no session reported limits; clicking it opens the panel. */
@Composable
private fun UsageChip(meters: List<UsageMeter>, active: Boolean, onClick: () -> Unit) {
    WithTooltip("Usage", hint = AppShortcut.USAGE.hint()) {
        Pressable(onClick, Modifier.height(chipHeight), shape = RoundedCornerShape(Radius.sm + 1.dp), base = if (active) colors.accentSoft else Color.Transparent) {
            Row(Modifier.padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (meters.isEmpty()) {
                    Glyph(Icons.Gauge, colors.textTertiary, size = IconSize.medium)
                    Label("No usage data", color = colors.textTertiary, style = Type.caption)
                }
                meters.forEach { UsageBar(it) }
            }
        }
    }
}

@Composable
private fun UsageBar(meter: UsageMeter) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
        Label(meter.label, color = colors.textTertiary, style = Type.caption)
        Box(Modifier.width(barWidth).height(barHeight).background(colors.border, RoundedCornerShape(Radius.pill))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(meter.fraction).background(levelColor(meter.level), RoundedCornerShape(Radius.pill)))
        }
        Label(meter.percentText, color = colors.textSecondary, style = Type.numericCaption)
    }
}

@Composable
internal fun levelColor(level: UsageLevel): Color = when (level) {
    UsageLevel.NORMAL -> colors.success
    UsageLevel.HIGH -> colors.warning
    UsageLevel.CRITICAL -> colors.danger
}
