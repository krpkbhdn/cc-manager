package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Window

private val edge = 5.dp
private val corner = 10.dp

private enum class Side(val cursor: Int, val left: Boolean = false, val right: Boolean = false, val top: Boolean = false, val bottom: Boolean = false) {
    N(Cursor.N_RESIZE_CURSOR, top = true),
    S(Cursor.S_RESIZE_CURSOR, bottom = true),
    W(Cursor.W_RESIZE_CURSOR, left = true),
    E(Cursor.E_RESIZE_CURSOR, right = true),
    NW(Cursor.NW_RESIZE_CURSOR, left = true, top = true),
    NE(Cursor.NE_RESIZE_CURSOR, right = true, top = true),
    SW(Cursor.SW_RESIZE_CURSOR, left = true, bottom = true),
    SE(Cursor.SE_RESIZE_CURSOR, right = true, bottom = true),
}

/** Invisible grab handles on the window edges; an undecorated window has no native resize border. */
@Composable
fun ResizeFrame(window: Window, enabled: Boolean) {
    if (!enabled) return
    Box(Modifier.fillMaxSize()) {
        Handle(window, Side.N, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(edge))
        Handle(window, Side.S, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(edge))
        Handle(window, Side.W, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(edge))
        Handle(window, Side.E, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(edge))
        Handle(window, Side.NW, Modifier.align(Alignment.TopStart).size(corner))
        Handle(window, Side.NE, Modifier.align(Alignment.TopEnd).size(corner))
        Handle(window, Side.SW, Modifier.align(Alignment.BottomStart).size(corner))
        Handle(window, Side.SE, Modifier.align(Alignment.BottomEnd).size(corner))
    }
}

@Composable
private fun Handle(window: Window, side: Side, modifier: Modifier) {
    var origin = Rectangle()
    var anchor = java.awt.Point()
    Box(
        modifier.pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(side.cursor))).pointerInput(side) {
            detectDragGestures(
                onDragStart = { origin = window.bounds; anchor = MouseInfo.getPointerInfo().location },
                onDrag = { change, _ ->
                    change.consume()
                    val now = MouseInfo.getPointerInfo().location
                    window.bounds = resized(origin, side, now.x - anchor.x, now.y - anchor.y, window.minimumSize)
                },
            )
        },
    )
}

private fun resized(start: Rectangle, side: Side, dx: Int, dy: Int, min: java.awt.Dimension): Rectangle {
    var x = start.x
    var y = start.y
    var width = start.width
    var height = start.height
    if (side.right) width = (start.width + dx).coerceAtLeast(min.width)
    if (side.bottom) height = (start.height + dy).coerceAtLeast(min.height)
    if (side.left) {
        width = (start.width - dx).coerceAtLeast(min.width)
        x = start.x + start.width - width
    }
    if (side.top) {
        height = (start.height - dy).coerceAtLeast(min.height)
        y = start.y + start.height - height
    }
    return Rectangle(x, y, width, height)
}
