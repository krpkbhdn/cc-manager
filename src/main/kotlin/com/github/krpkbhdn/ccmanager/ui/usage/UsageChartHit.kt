package com.github.krpkbhdn.ccmanager.ui.usage

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

const val BAR_FILL = 0.7f
private const val EXACT_DECIMALS = "%.4f"
private const val PERCENT_FORMAT = "%.1f%%"
private const val PERCENT = 100.0
private val tooltipDayFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

/** The day column under the pointer, or null in the gap between bars; [segment] is null above the stack. */
data class DayHit(val day: Int, val segment: Int?)

/** One tooltip row; [projectId] and [swatch] let the view paint the project colour dot. */
data class TooltipRow(val label: String, val value: String, val projectId: Long? = null, val swatch: Boolean = false)

data class TooltipContent(val title: String, val rows: List<TooltipRow>)

/** Geometry shared by drawing and hit-testing so they cannot diverge. */
fun barWidth(width: Float, count: Int): Float = width / count.coerceAtLeast(1) * BAR_FILL

fun barLeft(index: Int, width: Float, count: Int): Float = index * (width / count.coerceAtLeast(1)) + (width / count.coerceAtLeast(1) - barWidth(width, count)) / 2f

fun barHeight(costUsd: Double, peak: Double, height: Float): Float = (costUsd / peak * height).toFloat()

/** Only the drawn bar counts: its x range, from the baseline up to its real top. */
fun dayHit(days: List<DayBar>, x: Float, y: Float, width: Float, height: Float, peak: Double): DayHit? {
    if (days.isEmpty() || x < 0f || x >= width || y < 0f || y > height) return null
    val index = floor(x / (width / days.size)).toInt()
    val left = barLeft(index, width, days.size)
    val top = height - barHeight(days[index].totalUsd, peak, height)
    if (days[index].totalUsd <= 0.0 || x < left || x > left + barWidth(width, days.size) || y < top) return null
    return DayHit(index, segmentAt(days[index].slices, y, height, peak))
}

/** True over the filled part of a model bar, [x] and [y] local to the bar canvas. */
fun modelBarHit(x: Float, y: Float, width: Float, height: Float, costUsd: Double, peak: Double): Boolean =
    y in 0f..height && x >= 0f && costUsd > 0.0 && x <= (width * costUsd / peak).toFloat()

/** Slices stack upwards from the bottom, so the first slice sits lowest. */
fun segmentAt(slices: List<ProjectSlice>, y: Float, height: Float, peak: Double): Int? {
    var bottom = height
    slices.forEachIndexed { index, slice ->
        val top = bottom - barHeight(slice.costUsd, peak, height)
        if (y <= bottom && y >= top) return index
        bottom = top
    }
    return null
}

/** Exact cost: four decimals, but a plain zero for an empty day. */
fun exactCost(usd: Double): String = if (usd == 0.0) "$0.00" else "$" + EXACT_DECIMALS.format(Locale.ROOT, usd)

fun dayTooltip(day: DayBar, projectName: (Long) -> String): TooltipContent = TooltipContent(
    day.day.format(tooltipDayFormat),
    listOf(TooltipRow("Total", exactCost(day.totalUsd))) +
        day.slices.map { TooltipRow(it.projectId?.let(projectName) ?: "Other", exactCost(it.costUsd), it.projectId, swatch = true) },
)

fun modelTooltip(model: ModelTotal, models: List<ModelTotal>): TooltipContent {
    val total = models.sumOf { it.costUsd }
    val share = if (total > 0.0) model.costUsd / total * PERCENT else 0.0
    return TooltipContent(model.model, listOf(TooltipRow("Cost", exactCost(model.costUsd)), TooltipRow("Share of period", PERCENT_FORMAT.format(Locale.ROOT, share))))
}

/** Top-left corner of a tooltip placed right and below the pointer, pulled back inside the bounds. */
fun tooltipOrigin(pointerX: Float, pointerY: Float, tooltipWidth: Float, tooltipHeight: Float, boundsWidth: Float, boundsHeight: Float, gap: Float): Pair<Float, Float> {
    val x = if (pointerX + gap + tooltipWidth <= boundsWidth) pointerX + gap else pointerX - gap - tooltipWidth
    val y = if (pointerY + gap + tooltipHeight <= boundsHeight) pointerY + gap else pointerY - gap - tooltipHeight
    return x.coerceIn(0f, (boundsWidth - tooltipWidth).coerceAtLeast(0f)) to y.coerceIn(0f, (boundsHeight - tooltipHeight).coerceAtLeast(0f))
}
