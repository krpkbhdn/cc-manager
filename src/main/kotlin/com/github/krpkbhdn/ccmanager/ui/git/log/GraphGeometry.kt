package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.ui.graphics.Color

const val GRAPH_COLOR_COUNT = 8

/** A cubic Bezier in row-local coordinates: start, two control points, end. */
data class Cubic(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float)

/** The two halves of one link, split at the boundary between its row and the next: [lower] is drawn in the row, [upper] in the next one. */
data class LinkSegments(val lower: Cubic, val upper: Cubic)

/** Horizontal centre of lane [lane]. */
fun laneX(lane: Int, laneWidth: Float): Float = lane * laneWidth + laneWidth / 2f

/** Width of the graph column for [laneCount] lanes. */
fun graphWidth(laneCount: Int, laneWidth: Float): Float = maxOf(laneCount, 1) * laneWidth

/**
 * The S-curve from (lane [fromLane], middle of this row) to (lane [toLane], middle of the next row) cut in two at the
 * row boundary. Vertical links degenerate to straight lines. De Casteljau at t = 0.5 keeps both halves smooth.
 */
fun linkSegments(fromLane: Int, toLane: Int, rowHeight: Float, laneWidth: Float): LinkSegments {
    val xa = laneX(fromLane, laneWidth)
    val xb = laneX(toLane, laneWidth)
    val half = rowHeight / 2f
    val xm = (xa + xb) / 2f
    val xc1 = (xa + xm) / 2f
    val xc2 = (xm + xb) / 2f
    val lower = Cubic(xa, half, xa, rowHeight * LOWER_CONTROL, xc1, rowHeight * LOWER_CONTROL_END, xm, rowHeight)
    val upper = Cubic(xm, 0f, xc2, half * UPPER_CONTROL, xb, half * UPPER_CONTROL_END, xb, half)
    return LinkSegments(lower, upper)
}

private const val LOWER_CONTROL = 0.75f
private const val LOWER_CONTROL_END = 0.875f
private const val UPPER_CONTROL = 0.25f
private const val UPPER_CONTROL_END = 0.5f

private val darkLanes = listOf(0xFFD97757, 0xFF5BA4F5, 0xFF3DD68C, 0xFFE0A93B, 0xFFB583F0, 0xFF3CC6C9, 0xFFF0709A, 0xFF9AA5B8)
private val lightLanes = listOf(0xFFB04F2E, 0xFF2A6FD0, 0xFF0F7A4F, 0xFFA85F00, 0xFF7A45C8, 0xFF0C8A90, 0xFFC4296A, 0xFF5E6878)

/** The 8-slot lane palette, tuned separately per theme so every colour keeps contrast on its background. */
fun graphColor(slot: Int, dark: Boolean): Color =
    Color((if (dark) darkLanes else lightLanes)[Math.floorMod(slot, GRAPH_COLOR_COUNT)])
