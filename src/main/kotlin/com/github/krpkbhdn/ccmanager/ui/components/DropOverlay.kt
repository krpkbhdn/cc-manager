package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val inset = 16.dp
private val iconBox = 56.dp
private val dash = 10.dp

/** Full-window hint while a folder is dragged over the app. */
@Composable
fun DropOverlay(modifier: Modifier = Modifier) {
    val accent = colors.accent
    Box(
        modifier.fillMaxSize().background(colors.scrim).padding(inset).drawBehind {
            val width = 2.dp.toPx()
            drawRoundRect(
                accent, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), CornerRadius(Radius.xl.toPx()),
                style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx() / 2))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(iconBox).clip(RoundedCornerShape(Radius.xl)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                Glyph(Icons.FolderPlus, accent, size = 28.dp)
            }
            Label("Drop to add this folder as a project", style = Type.title, color = colors.textPrimary)
            Label("Claude sessions for it will be listed in the sidebar.", color = colors.textSecondary.copy(alpha = 1f - Alpha.FAINT))
        }
    }
}
