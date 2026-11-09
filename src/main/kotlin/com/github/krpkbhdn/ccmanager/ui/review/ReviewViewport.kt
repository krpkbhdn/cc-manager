package com.github.krpkbhdn.ccmanager.ui.review

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val FIT_MARGIN_PX = 16f
private const val MIN_SCALE_OF_FIT = 0.25f
private const val MAX_SCALE = 16f
private const val WHEEL_STEP = 1.1f
private const val MIN_VISIBLE_PX = 64f

/** Screen position of the image: a screen point is `image * scale + offset`. */
data class Viewport(val scale: Float, val offsetX: Float, val offsetY: Float) {

    fun zoomAt(anchorX: Float, anchorY: Float, factor: Float, minScale: Float = 0f): Viewport {
        val target = (scale * factor).coerceIn(minScale, max(minScale, MAX_SCALE))
        val ratio = target / scale
        return Viewport(target, anchorX - (anchorX - offsetX) * ratio, anchorY - (anchorY - offsetY) * ratio)
    }

    fun pan(dx: Float, dy: Float) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    fun toImage(screenX: Float, screenY: Float): Pair<Float, Float> =
        Pair((screenX - offsetX) / scale, (screenY - offsetY) / scale)

    fun toScreen(imageX: Float, imageY: Float): Pair<Float, Float> =
        Pair(imageX * scale + offsetX, imageY * scale + offsetY)

    /** Keeps at least a strip of the image on screen so it cannot be lost. */
    fun clamped(imageW: Float, imageH: Float, viewW: Float, viewH: Float) = copy(
        offsetX = clampOffset(offsetX, imageW * scale, viewW),
        offsetY = clampOffset(offsetY, imageH * scale, viewH),
    )

    companion object {
        fun fit(imageW: Float, imageH: Float, viewW: Float, viewH: Float): Viewport {
            val scale = fitScale(imageW, imageH, viewW, viewH)
            return Viewport(scale, (viewW - imageW * scale) / 2, (viewH - imageH * scale) / 2)
        }

        fun actualSize(imageW: Float, imageH: Float, viewW: Float, viewH: Float) =
            Viewport(1f, (viewW - imageW) / 2, (viewH - imageH) / 2)

        fun fitScale(imageW: Float, imageH: Float, viewW: Float, viewH: Float): Float =
            max(min((viewW - 2 * FIT_MARGIN_PX) / imageW, (viewH - 2 * FIT_MARGIN_PX) / imageH), Float.MIN_VALUE)

        fun minScale(imageW: Float, imageH: Float, viewW: Float, viewH: Float): Float =
            fitScale(imageW, imageH, viewW, viewH) * MIN_SCALE_OF_FIT

        /** Wheel notches to a zoom factor; a positive delta (scroll down) zooms out. */
        fun zoomFactor(scrollDelta: Float): Float = WHEEL_STEP.pow(-scrollDelta)
    }
}

private fun clampOffset(offset: Float, imageExtent: Float, viewExtent: Float): Float {
    val visible = min(MIN_VISIBLE_PX, imageExtent)
    return offset.coerceIn(visible - imageExtent, viewExtent - visible)
}
