package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val minHeight = 168.dp
private val plusBox = 36.dp
private val dashLength = 6.dp
private val dashGap = 5.dp

/** Dashed tile at the end of the project grid that starts the add-project flow. */
@Composable
fun AddProjectCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.xl)
    val idle = colors.borderStrong
    val active = colors.accent
    Pressable(onClick, modifier.fillMaxWidth().heightIn(min = minHeight), shape) { hovered ->
        Box(
            Modifier.matchParentSize().drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    color = if (hovered) active else idle,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(Radius.xl.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), dashGap.toPx()))),
                )
            },
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(plusBox).clip(RoundedCornerShape(Radius.md)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                Glyph(Icons.Plus, colors.accent, size = IconSize.xlarge)
            }
            Label("Add project", style = Type.bodyMedium)
            Label("or drop a folder here", color = colors.textTertiary, style = Type.small)
        }
    }
}
