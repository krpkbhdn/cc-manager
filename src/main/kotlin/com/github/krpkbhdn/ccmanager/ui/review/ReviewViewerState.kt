package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

private const val BUTTON_ZOOM_STEP = 1.25f

/** Zoom and pan of one image in a pane of [size]; every resize refits, so a new image or window size always starts fully visible. */
@Stable
class ReviewViewerState(val imageW: Float, val imageH: Float) {
    var size by mutableStateOf(IntSize.Zero)
        private set
    var viewport by mutableStateOf<Viewport?>(null)
        private set

    fun resize(newSize: IntSize) {
        size = newSize
        viewport = if (newSize.width > 0 && newSize.height > 0) Viewport.fit(imageW, imageH, newSize.width.toFloat(), newSize.height.toFloat()) else null
    }

    fun fit() = resize(size)

    fun actualSize() {
        if (viewport != null) viewport = Viewport.actualSize(imageW, imageH, viewW(), viewH())
    }

    fun zoomAt(anchorX: Float, anchorY: Float, factor: Float) {
        val current = viewport ?: return
        val minScale = Viewport.minScale(imageW, imageH, viewW(), viewH())
        viewport = current.zoomAt(anchorX, anchorY, factor, minScale).clamped(imageW, imageH, viewW(), viewH())
    }

    fun zoomIn() = zoomAt(viewW() / 2, viewH() / 2, BUTTON_ZOOM_STEP)

    fun zoomOut() = zoomAt(viewW() / 2, viewH() / 2, 1 / BUTTON_ZOOM_STEP)

    fun panBy(dx: Float, dy: Float) {
        viewport = viewport?.pan(dx, dy)?.clamped(imageW, imageH, viewW(), viewH())
    }

    private fun viewW() = size.width.toFloat()
    private fun viewH() = size.height.toFloat()
}
