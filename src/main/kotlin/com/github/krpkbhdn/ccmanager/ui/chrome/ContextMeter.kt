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
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.ContextUsageView
import com.github.krpkbhdn.ccmanager.ui.state.UsageLevel
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val barWidth = 28.dp
private val barHeight = 4.dp
private val chipHeight = 22.dp
private const val NO_DATA = "ctx --"

/** Tab strip chip: how full the selected session's context window is; clicking it opens the details. */
@Composable
internal fun ContextMeter(view: ContextUsageView?, onClick: () -> Unit) {
    val tooltip = listOfNotNull("Context usage", view?.tokensText?.let { "$it tokens" }).joinToString(": ")
    WithTooltip(tooltip, hint = AppShortcut.CONTEXT_USAGE.hint()) {
        Pressable(onClick, Modifier.height(chipHeight), shape = RoundedCornerShape(Radius.sm + 1.dp), base = Color.Transparent) {
            Row(Modifier.fillMaxHeight().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (view == null) {
                    Label(NO_DATA, color = colors.textTertiary, style = Type.numericCaption)
                } else {
                    Label("ctx", color = colors.textTertiary, style = Type.caption)
                    ContextBar(view.fraction, view.level)
                    Label(view.percentText, color = colors.textSecondary, style = Type.numericCaption)
                }
            }
        }
    }
}

/** A progress bar coloured by [level]; the caller's [modifier] sets its size, by default the compact chip bar. */
@Composable
internal fun ContextBar(fraction: Float, level: UsageLevel, modifier: Modifier = Modifier.width(barWidth).height(barHeight)) {
    Box(modifier.background(colors.border, RoundedCornerShape(Radius.pill))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(levelColor(level), RoundedCornerShape(Radius.pill)))
    }
}
