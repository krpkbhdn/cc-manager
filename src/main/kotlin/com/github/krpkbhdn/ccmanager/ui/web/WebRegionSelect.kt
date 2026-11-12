package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.LocalAppColors
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import java.awt.Cursor

private const val DIM_ALPHA = 0.45f
private const val LABEL_GAP_DP = 4f
private const val LABEL_HEIGHT_DP = 22f
private const val HINT_TOP_DP = 12

/**
 * Full-canvas drag-to-select overlay: swallows every pointer event so the page never sees the drag; only the primary button drags. Releasing a usable
 * rectangle calls [onSelected]; a too small one keeps the overlay armed; Esc calls [onCancel].
 */
@Composable
fun WebRegionSelect(
    viewport: PageViewport,
    onSelected: (CssRect) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current.density
    val focus = remember { FocusRequester() }
    val currentViewport = rememberUpdatedState(viewport)
    val currentSelected = rememberUpdatedState(onSelected)
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val selection = drag?.let { (start, end) -> RegionSelection.cssRect(viewport, start, end) }
    val canvasRect = selection?.let { RegionSelection.canvasRect(viewport, it) }

    Box(
        modifier.fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onCancel(); true } else false }
            .focusable()
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)))
            .pointerInput(density) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.first()
                        val point = Offset(change.position.x / density, change.position.y / density)
                        val pressed = event.buttons.isPrimaryPressed
                        if (event.type == PointerEventType.Press) focus.requestFocus()
                        val current = drag
                        drag = when {
                            pressed && current == null -> point to point
                            pressed && current != null -> current.first to point
                            else -> null
                        }
                        if (!pressed && current != null) {
                            val rect = RegionSelection.cssRect(currentViewport.value, current.first, point)
                            if (rect.isUsable()) currentSelected.value(rect)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = colors.background.copy(alpha = DIM_ALPHA)
            if (canvasRect == null) {
                drawRect(dim)
                return@Canvas
            }
            val hole = Rect(canvasRect.topLeft * density, canvasRect.bottomRight * density)
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRect(hole)
            }
            drawPath(path, dim)
            drawRect(colors.accent, hole.topLeft, hole.size, style = Stroke(1.dp.toPx()))
        }
        if (canvasRect == null) {
            Label(
                "Drag to select a region - Esc to cancel", Modifier.align(Alignment.TopCenter).padding(top = HINT_TOP_DP.dp)
                    .background(colors.surface, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                color = colors.textSecondary, style = Type.caption,
            )
        }
        if (canvasRect != null && selection != null) {
            val above = canvasRect.top >= LABEL_HEIGHT_DP + LABEL_GAP_DP
            val y = if (above) canvasRect.top - LABEL_HEIGHT_DP - LABEL_GAP_DP else canvasRect.top + LABEL_GAP_DP
            Label(
                RegionSelection.label(selection),
                Modifier.offset { IntOffset((canvasRect.left * density).toInt() + LABEL_GAP_DP.dp.roundToPx(), (y * density).toInt()) }
                    .background(colors.accent, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = colors.onAccent, style = Type.monoSmall,
            )
        }
    }
}
