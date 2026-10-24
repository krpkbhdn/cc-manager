package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import androidx.compose.foundation.background

private const val TOOLTIP_DELAY_MILLIS = 450
private val tooltipGap = 8.dp
private val tooltipMaxWidth = 320.dp
private val tooltipElevation = 8.dp

/** Wraps [content] in a hover tooltip showing [text] (and an optional shortcut [hint]); no tooltip when [text] is null. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithTooltip(text: String?, modifier: Modifier = Modifier, hint: String? = null, content: @Composable () -> Unit) {
    if (text == null) return content()
    TooltipArea(
        tooltip = { TooltipBubble(text, hint) },
        modifier = modifier,
        delayMillis = TOOLTIP_DELAY_MILLIS,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            alignment = Alignment.BottomCenter,
            offset = DpOffset(0.dp, tooltipGap),
        ),
        content = content,
    )
}

@Composable
private fun TooltipBubble(text: String, hint: String?) {
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        Modifier.shadow(tooltipElevation, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .widthIn(max = tooltipMaxWidth)
            .padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.xs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Label(text, style = Type.small, maxLines = 4)
        hint?.let { Label(it, color = colors.textTertiary, style = Type.monoSmall) }
    }
}
