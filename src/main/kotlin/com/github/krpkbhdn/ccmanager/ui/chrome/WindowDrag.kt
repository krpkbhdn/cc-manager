package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window

/** Moves [window] while the modified area is dragged; uses screen coordinates so the moving window cannot skew the delta. */
fun Modifier.windowDrag(window: Window, enabled: Boolean): Modifier =
    if (!enabled) this else pointerInput(window) {
        var origin = Point()
        var anchor = Point()
        detectDragGestures(
            onDragStart = { origin = window.location; anchor = MouseInfo.getPointerInfo().location },
            onDrag = { change, _ ->
                change.consume()
                val now = MouseInfo.getPointerInfo().location
                window.setLocation(origin.x + now.x - anchor.x, origin.y + now.y - anchor.y)
            },
        )
    }
