package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

/** Crops a DIP rectangle out of a frame painted at [scale] physical pixels per DIP and encodes it as PNG. */
object FrameCrop {
    fun png(image: Image?, scale: Double, xDip: Int, yDip: Int, widthDip: Int, heightDip: Int): ByteArray? {
        if (image == null || widthDip <= 0 || heightDip <= 0) return null
        val left = (xDip * scale).roundToInt().coerceAtLeast(0)
        val top = (yDip * scale).roundToInt().coerceAtLeast(0)
        val right = ((xDip + widthDip) * scale).roundToInt().coerceAtMost(image.width)
        val bottom = ((yDip + heightDip) * scale).roundToInt().coerceAtMost(image.height)
        if (right <= left || bottom <= top) return null
        Surface.makeRasterN32Premul(right - left, bottom - top).use { surface ->
            surface.canvas.drawImageRect(
                image,
                Rect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
                Rect.makeWH((right - left).toFloat(), (bottom - top).toFloat()),
            )
            return surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG)?.bytes }
        }
    }
}

/** Whether a frame of [frameWidthPx] x [frameHeightPx] belongs to a view of the given DIP size; fractional densities round, so a couple of pixels are allowed. */
internal fun frameFits(frameWidthPx: Int, frameHeightPx: Int, viewWidthDip: Int, viewHeightDip: Int, density: Double): Boolean =
    kotlin.math.abs(frameWidthPx - viewWidthDip * density) <= FRAME_SIZE_TOLERANCE_PX &&
        kotlin.math.abs(frameHeightPx - viewHeightDip * density) <= FRAME_SIZE_TOLERANCE_PX

private const val FRAME_SIZE_TOLERANCE_PX = 2.0
