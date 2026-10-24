package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val buttonWidth = 46.dp
private val glyphSize = 10.dp
private val strokeWidth = 1.dp

/** Minimise, maximise/restore and close, drawn to match the rest of the chrome. */
@Composable
fun WindowButtons(controls: WindowControls) {
    Row(Modifier.fillMaxHeight()) {
        CaptionButton(controls.minimize, hoverIsDanger = false) { Glyph10 { drawMinimize(it) } }
        CaptionButton(controls.toggleMaximize, hoverIsDanger = false) { Glyph10 { if (controls.maximized) drawRestore(it) else drawMaximize(it) } }
        CaptionButton(controls.close, hoverIsDanger = true) { hovered -> Glyph(Icons.Close, if (hovered) Color.White else colors.textSecondary, size = IconSize.medium) }
    }
}

@Composable
private fun CaptionButton(onClick: () -> Unit, hoverIsDanger: Boolean, content: @Composable (hovered: Boolean) -> Unit) {
    Pressable(onClick, Modifier.width(buttonWidth).fillMaxHeight(), shape = RoundedCornerShape(0.dp), hover = if (hoverIsDanger) colors.danger else colors.hoverOverlay) { hovered ->
        Box(Modifier.align(Alignment.Center)) { content(hovered) }
    }
}

@Composable
private fun Glyph10(draw: DrawScope.(Color) -> Unit) {
    val tint = colors.textSecondary
    Canvas(Modifier.size(glyphSize)) { draw(tint) }
}

private fun DrawScope.drawMinimize(tint: Color) {
    drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth.toPx())
}

private fun DrawScope.drawMaximize(tint: Color) {
    drawRect(tint, Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2), Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx()), style = Stroke(strokeWidth.toPx()))
}

private fun DrawScope.drawRestore(tint: Color) {
    val w = strokeWidth.toPx()
    val inner = size.width * RESTORE_RATIO
    drawRect(tint, Offset(w / 2, size.height - inner + w / 2), Size(inner - w, inner - w), style = Stroke(w))
    drawRect(tint, Offset(size.width - inner + w / 2, w / 2), Size(inner - w, inner - w), style = Stroke(w))
}

private const val RESTORE_RATIO = 0.75f
