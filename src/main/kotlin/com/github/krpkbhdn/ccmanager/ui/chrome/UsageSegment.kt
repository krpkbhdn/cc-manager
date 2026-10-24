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
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.state.UsageLevel
import com.github.krpkbhdn.ccmanager.ui.state.UsageMeter
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val barWidth = 28.dp
private val barHeight = 4.dp

/** Subscription usage in the status bar: one small bar per window, muted text while no session reported limits yet. */
@Composable
fun UsageSegment(meters: List<UsageMeter>) {
    Row(Modifier.padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (meters.isEmpty()) {
            WithTooltip("Usage limits appear after a Claude session gets its first reply") {
                Label("No usage data", color = colors.textTertiary, style = Type.caption)
            }
        }
        meters.forEach { UsageBar(it) }
    }
}

@Composable
private fun UsageBar(meter: UsageMeter) {
    WithTooltip(meter.tooltip) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            Label(meter.label, color = colors.textTertiary, style = Type.caption)
            Box(Modifier.width(barWidth).height(barHeight).background(colors.border, RoundedCornerShape(Radius.pill))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(meter.fraction).background(levelColor(meter.level), RoundedCornerShape(Radius.pill)))
            }
            Label(meter.percentText, color = colors.textSecondary, style = Type.numericCaption)
        }
    }
}

@Composable
private fun levelColor(level: UsageLevel): Color = when (level) {
    UsageLevel.NORMAL -> colors.success
    UsageLevel.HIGH -> colors.warning
    UsageLevel.CRITICAL -> colors.danger
}
