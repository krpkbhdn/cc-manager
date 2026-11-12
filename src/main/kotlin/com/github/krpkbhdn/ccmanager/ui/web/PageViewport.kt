package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The page laid out at [widthCss] x [heightCss] CSS pixels, shown at [scale] and placed at ([offsetX], [offsetY]) in
 * canvas dp. In responsive mode the page fills the canvas: scale 1, offset 0. A page larger than the canvas at its zoom is
 * scrolled by [panX]/[panY] (canvas dp, within 0..[maxPanX]/[maxPanY]); a smaller one is centered.
 */
data class PageViewport(
    val widthCss: Int,
    val heightCss: Int,
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val deviceScaleFactor: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val maxPanX: Double = 0.0,
    val maxPanY: Double = 0.0,
) {
    fun toPage(canvas: Offset): Offset = Offset(((canvas.x - offsetX) / scale).toFloat(), ((canvas.y - offsetY) / scale).toFloat())

    fun toCanvas(page: Offset): Offset = Offset((page.x * scale + offsetX).toFloat(), (page.y * scale + offsetY).toFloat())

    fun containsCanvas(canvas: Offset): Boolean {
        val p = toPage(canvas)
        return p.x >= 0 && p.y >= 0 && p.x < widthCss && p.y < heightCss
    }

    /**
     * Frame pixels per CSS pixel, i.e. the devicePixelRatio the page sees: enough to be crisp at the current zoom, never
     * below the OS [density] times the preset's [deviceScaleFactor], reduced so the frame never exceeds [MAX_FRAME_PIXELS].
     */
    fun renderScale(density: Float): Double {
        val wanted = max(density * scale, density * deviceScaleFactor)
        val cap = sqrt(MAX_FRAME_PIXELS / (widthCss.toDouble() * heightCss))
        return min(wanted, cap)
    }

    /** The zoom one step of [notches] (positive zooms in) from the current one, with the pan that keeps the page point under [cursor] (canvas dp) fixed. */
    fun zoomAt(cursor: Offset, notches: Int): ZoomChange {
        val current = (scale * 100).roundToInt()
        var percent = current
        repeat(abs(notches)) {
            percent = if (notches > 0) ZOOM_STEPS.firstOrNull { it > percent } ?: percent else ZOOM_STEPS.lastOrNull { it < percent } ?: percent
        }
        val newScale = percent / 100.0
        val page = toPage(cursor)
        return ZoomChange(percent, page.x * newScale - cursor.x, page.y * newScale - cursor.y)
    }

    /** [current] (the stored pan, not this viewport's) moved by the drag delta and clamped to this viewport's bounds. */
    fun panned(current: Offset, dx: Double, dy: Double): Offset =
        Offset((current.x + dx).coerceIn(0.0, maxPanX).toFloat(), (current.y + dy).coerceIn(0.0, maxPanY).toFloat())

    /** [panX]/[panY] are unclamped; [PageViewport.of] clamps them. */
    data class ZoomChange(val percent: Int, val panX: Double, val panY: Double)

    companion object {
        val ZOOM_STEPS = listOf(25, 33, 50, 67, 75, 100, 125, 150, 200, 300, 400)
        const val MAX_FRAME_PIXELS = 16_000_000.0
        const val FIT_LIMIT = 1.0

        /** Keeps a not yet measured (zero sized) canvas from producing a zero scale. */
        const val MIN_SCALE = 0.01

        /** [fit] only ever shrinks unless [allowUpscale]. */
        fun fit(widthCss: Int, heightCss: Int, availWidth: Double, availHeight: Double, allowUpscale: Boolean = false): Double {
            val ratio = min(availWidth / widthCss, availHeight / heightCss)
            return if (allowUpscale) ratio else min(ratio, FIT_LIMIT)
        }

        /** The pan to store after [change] on [zoomed] (already at the new zoom): clamped for the new zoom, so the next drag starts from what is shown. */
        fun panAfterZoom(zoomed: DeviceSelection, change: ZoomChange, availWidth: Double, availHeight: Double): Offset {
            val v = of(zoomed, availWidth, availHeight, change.panX, change.panY)
            return Offset(v.panX.toFloat(), v.panY.toFloat())
        }

        fun of(selection: DeviceSelection, availWidth: Double, availHeight: Double, panX: Double = 0.0, panY: Double = 0.0): PageViewport {
            val w = availWidth.toInt().coerceAtLeast(1)
            val h = availHeight.toInt().coerceAtLeast(1)
            if (selection.responsive) return PageViewport(w, h)
            val pageW = selection.layoutWidth
            val pageH = selection.layoutHeight
            val scale = when (val zoom = selection.zoom) {
                DeviceZoom.Fit -> fit(pageW, pageH, availWidth, availHeight)
                is DeviceZoom.Percent -> zoom.value / 100.0
            }.coerceAtLeast(MIN_SCALE)
            val maxPanX = (pageW * scale - availWidth).coerceAtLeast(0.0)
            val maxPanY = (pageH * scale - availHeight).coerceAtLeast(0.0)
            val x = panX.coerceIn(0.0, maxPanX)
            val y = panY.coerceIn(0.0, maxPanY)
            return PageViewport(
                pageW, pageH, scale,
                if (maxPanX > 0) -x else (availWidth - pageW * scale) / 2,
                if (maxPanY > 0) -y else (availHeight - pageH * scale) / 2,
                selection.preset?.deviceScaleFactor ?: 1.0,
                x, y, maxPanX, maxPanY,
            )
        }
    }
}
