package com.github.krpkbhdn.ccmanager.ui.web.browser

import com.github.krpkbhdn.ccmanager.review.ReviewService
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** What to photograph; Region is in CSS px relative to the viewport. */
sealed interface ShotArea {
    data object Visible : ShotArea
    data object WholePage : ShotArea
    /** [scroll] is the page scroll when the region was selected; without it the scroll at capture time is used. */
    data class Region(val xCss: Double, val yCss: Double, val widthCss: Double, val heightCss: Double, val scroll: PageScroll? = null) : ShotArea
}

/** The page scroll offset in CSS px. */
data class PageScroll(val x: Double, val y: Double)

/** Output ceilings: longest side and pixel count of the image, byte size of the encoded file. */
typealias ShotLimits = ReviewService.CaptureLimits

/** A captured image; css sizes are of the photographed area, `scale` the factor applied on top of the device scale factor. */
class PageShot(val bytes: ByteArray, val widthPx: Int, val heightPx: Int, val cssWidth: Double, val cssHeight: Double, val scale: Double, val cut: Boolean)

/** Page geometry read right before the capture; sizes in CSS px. */
data class PageMetrics(val scrollX: Double, val scrollY: Double, val scrollWidth: Int, val scrollHeight: Int, val viewWidth: Int, val viewHeight: Int, val dpr: Double)

/** The capture parameters for one area: pure, so the limits can be tested without a browser. `clip` is in document CSS px. */
data class ShotPlan(val clip: Clip?, val scale: Double, val cut: Boolean, val beyondViewport: Boolean) {
    data class Clip(val x: Double, val y: Double, val width: Double, val height: Double)

    companion object {
        const val FLOOR_SCALE = 0.5
        private const val SHRINK = 0.999
        private const val EPSILON = 1e-6

        fun of(area: ShotArea, metrics: PageMetrics, limits: ShotLimits): ShotPlan = when (area) {
            ShotArea.Visible -> ShotPlan(null, 1.0, false, false)
            ShotArea.WholePage -> fit(0.0, 0.0, metrics.scrollWidth.toDouble(), metrics.scrollHeight.toDouble(), metrics.dpr, limits, true)
            is ShotArea.Region -> {
                val scroll = area.scroll ?: PageScroll(metrics.scrollX, metrics.scrollY)
                fit(area.xCss + scroll.x, area.yCss + scroll.y, area.widthCss, area.heightCss, metrics.dpr, limits, false)
            }
        }

        private fun fit(x: Double, y: Double, w: Double, h: Double, dpr: Double, limits: ShotLimits, beyond: Boolean): ShotPlan {
            val side = limits.maxSide().toDouble()
            val scale = minOf(1.0, side / (w * dpr), side / (h * dpr), sqrt(limits.maxPixels() / (w * h * dpr * dpr)))
            if (scale >= FLOOR_SCALE) return withinRoundedLimits(ShotPlan(Clip(x, y, w, h), scale, false, beyond), dpr, limits)
            val cutWidth = floor(min(w, side / (FLOOR_SCALE * dpr)))
            val cutHeight = floor(minOf(h, side / (FLOOR_SCALE * dpr), limits.maxPixels() / (cutWidth * FLOOR_SCALE * FLOOR_SCALE * dpr * dpr)))
            return withinRoundedLimits(ShotPlan(Clip(x, y, cutWidth, cutHeight), FLOOR_SCALE, true, beyond), dpr, limits)
        }

        /** The browser rounds each output side, so a plan sized exactly to the pixel cap can land above it: shrink until even rounded up it fits. */
        private fun withinRoundedLimits(plan: ShotPlan, dpr: Double, limits: ShotLimits): ShotPlan {
            val clip = plan.clip!!
            var scale = plan.scale
            while (scale > 0.0 && !fits(clip, scale * dpr, limits)) scale *= SHRINK
            return plan.copy(scale = scale)
        }

        private fun fits(clip: Clip, unit: Double, limits: ShotLimits): Boolean {
            val w = ceil(clip.width * unit - EPSILON).toLong()
            val h = ceil(clip.height * unit - EPSILON).toLong()
            return w <= limits.maxSide() && h <= limits.maxSide() && w * h <= limits.maxPixels()
        }
    }
}
