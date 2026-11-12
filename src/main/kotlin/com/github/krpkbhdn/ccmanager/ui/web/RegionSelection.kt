package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.min

/** A rectangle in viewport CSS pixels. */
data class CssRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    /** Big enough to be a deliberate selection rather than a click. */
    fun isUsable(): Boolean = width >= MIN_SIDE_CSS && height >= MIN_SIDE_CSS

    companion object {
        const val MIN_SIDE_CSS = 8.0
    }
}

/** The drag of a region selection, [start] and [current] in the canvas units of the [PageViewport] (dp). */
object RegionSelection {
    /** The dragged rectangle in CSS pixels, normalised for reverse drags and clamped to the page. */
    fun cssRect(viewport: PageViewport, start: Offset, current: Offset): CssRect {
        val a = viewport.toPage(start)
        val b = viewport.toPage(current)
        val left = min(a.x, b.x).toDouble().coerceIn(0.0, viewport.widthCss.toDouble())
        val top = min(a.y, b.y).toDouble().coerceIn(0.0, viewport.heightCss.toDouble())
        val right = maxOf(a.x, b.x).toDouble().coerceIn(0.0, viewport.widthCss.toDouble())
        val bottom = maxOf(a.y, b.y).toDouble().coerceIn(0.0, viewport.heightCss.toDouble())
        return CssRect(left, top, abs(right - left), abs(bottom - top))
    }

    /** [rect] in canvas units, for drawing. */
    fun canvasRect(viewport: PageViewport, rect: CssRect): Rect {
        val topLeft = viewport.toCanvas(Offset(rect.x.toFloat(), rect.y.toFloat()))
        val bottomRight = viewport.toCanvas(Offset((rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat()))
        return Rect(topLeft, bottomRight)
    }

    fun label(rect: CssRect): String = "${rect.width.toInt()} x ${rect.height.toInt()}"
}
