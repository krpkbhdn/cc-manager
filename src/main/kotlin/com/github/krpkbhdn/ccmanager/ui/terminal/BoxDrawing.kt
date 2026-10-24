package com.github.krpkbhdn.ccmanager.ui.terminal

import kotlin.math.max
import kotlin.math.roundToInt

private const val BOX_FIRST = 0x2500
private const val BOX_LAST = 0x257F
private const val BLOCK_FIRST = 0x2580
private const val BLOCK_LAST = 0x259F
private const val BOX_PREFIX = "BOX DRAWINGS "
private const val EIGHTHS = 8
private const val DOUBLE_SPAN_IN_LINES = 3
private const val HEAVY_NUMERATOR = 9
private const val HEAVY_DENOMINATOR = 4
private val SHADES = mapOf(0x2591 to 0.25f, 0x2592 to 0.5f, 0x2593 to 0.75f)
private val DASH_NOISE = setOf("DOUBLE", "TRIPLE", "QUADRUPLE", "DASH")

/** An axis-aligned rectangle in cell pixels. */
internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A rounded corner: a stroke of [width] from (startX, startY) to (endX, endY) bent towards (controlX, controlY). */
internal data class Arc(val startX: Float, val startY: Float, val controlX: Float, val controlY: Float, val endX: Float, val endY: Float, val width: Float)

/** Geometry of a box-drawing or block character: filled [rects] at [opacity] of the foreground, and an optional [arc]. */
internal class BoxShape(val rects: List<PixelRect>, val opacity: Float = 1f, val arc: Arc? = null)

/** Line weights per direction: 0 none, 1 light, 2 heavy, 3 double. */
private class Strokes(val left: Int, val right: Int, val up: Int, val down: Int, val rounded: Boolean)

private val strokesByCodePoint: Map<Int, Strokes> by lazy {
    (BOX_FIRST..BOX_LAST).mapNotNull { codePoint -> parseStrokes(Character.getName(codePoint))?.let { codePoint to it } }.toMap()
}

/** True for characters this file draws itself so that adjacent cells join without seams, whatever the font does. */
internal fun isGeometric(codePoint: Int): Boolean = codePoint in BLOCK_FIRST..BLOCK_LAST || (codePoint in BOX_FIRST..BOX_LAST && codePoint in strokesByCodePoint)

/** Shape of [codePoint] in a [width] x [height] cell, [lightWidth] pixels being the stroke of a light line; null if not geometric. */
internal fun boxShape(codePoint: Int, width: Int, height: Int, lightWidth: Int): BoxShape? = when (codePoint) {
    in BLOCK_FIRST..BLOCK_LAST -> blockShape(codePoint, width, height)
    in BOX_FIRST..BOX_LAST -> strokesByCodePoint[codePoint]?.let { lineShape(it, width, height, lightWidth) }
    else -> null
}

private fun fraction(total: Int, eighths: Int) = (total * eighths / EIGHTHS.toFloat()).roundToInt()

private fun blockShape(codePoint: Int, w: Int, h: Int): BoxShape? {
    val halfW = fraction(w, 4)
    val halfH = fraction(h, 4)
    SHADES[codePoint]?.let { return BoxShape(listOf(PixelRect(0, 0, w, h)), it) }
    val rects = when (codePoint) {
        0x2580 -> listOf(PixelRect(0, 0, w, halfH))
        in 0x2581..0x2588 -> listOf(PixelRect(0, h - fraction(h, codePoint - 0x2580), w, h))
        in 0x2589..0x258F -> listOf(PixelRect(0, 0, fraction(w, 0x2590 - codePoint), h))
        0x2590 -> listOf(PixelRect(halfW, 0, w, h))
        0x2594 -> listOf(PixelRect(0, 0, w, fraction(h, 1)))
        0x2595 -> listOf(PixelRect(w - fraction(w, 1), 0, w, h))
        in 0x2596..0x259F -> quadrants(codePoint, w, h, halfW, halfH)
        else -> return null
    }
    return BoxShape(rects)
}

private const val UPPER_LEFT = 1
private const val UPPER_RIGHT = 2
private const val LOWER_LEFT = 4
private const val LOWER_RIGHT = 8

/** Quadrant sets of U+2596..U+259F, in code point order. */
private val QUADRANT_MASKS = intArrayOf(
    LOWER_LEFT, LOWER_RIGHT, UPPER_LEFT,
    UPPER_LEFT or LOWER_LEFT or LOWER_RIGHT, UPPER_LEFT or LOWER_RIGHT, UPPER_LEFT or UPPER_RIGHT or LOWER_LEFT,
    UPPER_LEFT or UPPER_RIGHT or LOWER_RIGHT, UPPER_RIGHT, UPPER_RIGHT or LOWER_LEFT, UPPER_RIGHT or LOWER_LEFT or LOWER_RIGHT,
)

private fun quadrants(codePoint: Int, w: Int, h: Int, halfW: Int, halfH: Int): List<PixelRect> {
    val mask = QUADRANT_MASKS[codePoint - 0x2596]
    return buildList {
        if (mask and UPPER_LEFT != 0) add(PixelRect(0, 0, halfW, halfH))
        if (mask and UPPER_RIGHT != 0) add(PixelRect(halfW, 0, w, halfH))
        if (mask and LOWER_LEFT != 0) add(PixelRect(0, halfH, halfW, h))
        if (mask and LOWER_RIGHT != 0) add(PixelRect(halfW, halfH, w, h))
    }
}

private fun lineShape(strokes: Strokes, w: Int, h: Int, light: Int): BoxShape {
    val heavy = max(light + 1, light * HEAVY_NUMERATOR / HEAVY_DENOMINATOR)
    fun thickness(weight: Int) = when (weight) {
        1 -> light
        2 -> heavy
        3 -> light * DOUBLE_SPAN_IN_LINES
        else -> 0
    }
    val vertical = max(thickness(strokes.up), thickness(strokes.down))
    val horizontal = max(thickness(strokes.left), thickness(strokes.right))
    val centerX = w / 2f
    val centerY = h / 2f
    fun columnStart(span: Int) = (centerX - span / 2f).roundToInt()
    fun rowStart(span: Int) = (centerY - span / 2f).roundToInt()
    val rects = ArrayList<PixelRect>()
    fun bands(weight: Int, along: (Int, Int) -> Unit) {
        val span = thickness(weight)
        if (weight == 0) return
        if (weight == 3) {
            along(-light, light)
            along(light, light)
        } else {
            along(0, span)
        }
    }
    val reachX1 = columnStart(vertical) + vertical
    val reachX0 = columnStart(vertical)
    val reachY1 = rowStart(horizontal) + horizontal
    val reachY0 = rowStart(horizontal)
    bands(strokes.left) { shift, t -> rects += PixelRect(0, rowStart(t) + shift, reachX1, rowStart(t) + shift + t) }
    bands(strokes.right) { shift, t -> rects += PixelRect(reachX0, rowStart(t) + shift, w, rowStart(t) + shift + t) }
    bands(strokes.up) { shift, t -> rects += PixelRect(columnStart(t) + shift, 0, columnStart(t) + shift + t, reachY1) }
    bands(strokes.down) { shift, t -> rects += PixelRect(columnStart(t) + shift, reachY0, columnStart(t) + shift + t, h) }
    if (strokes.rounded) return BoxShape(emptyList(), arc = arcOf(strokes, w, h, light, columnStart(light), rowStart(light)))
    return BoxShape(rects)
}

private fun arcOf(strokes: Strokes, w: Int, h: Int, light: Int, columnStart: Int, rowStart: Int): Arc {
    val x = columnStart + light / 2f
    val y = rowStart + light / 2f
    val horizontalEdge = if (strokes.left > 0) 0f else w.toFloat()
    val verticalEdge = if (strokes.up > 0) 0f else h.toFloat()
    return Arc(horizontalEdge, y, x, y, x, verticalEdge, light.toFloat())
}

private fun parseStrokes(name: String?): Strokes? {
    if (name == null || !name.startsWith(BOX_PREFIX) || "DIAGONAL" in name) return null
    val rounded = " ARC " in name
    var words = name.removePrefix(BOX_PREFIX).replace("ARC ", "")
    if ("DASH" in words) words = words.split(' ').filterNot { it in DASH_NOISE }.joinToString(" ")
    val weights = IntArray(DIRECTIONS.size)
    var inherited = 0
    for (part in words.split(" AND ")) {
        val tokens = part.split(' ')
        val weight = tokens.firstNotNullOfOrNull(WEIGHTS::get) ?: inherited
        inherited = weight
        tokens.forEach { token -> DIRECTION_INDEXES[token]?.forEach { weights[it] = weight } }
    }
    return Strokes(weights[LEFT], weights[RIGHT], weights[UP], weights[DOWN], rounded)
}

private const val LEFT = 0
private const val RIGHT = 1
private const val UP = 2
private const val DOWN = 3
private val DIRECTIONS = listOf(LEFT, RIGHT, UP, DOWN)
private val WEIGHTS = mapOf("LIGHT" to 1, "SINGLE" to 1, "HEAVY" to 2, "DOUBLE" to 3)
private val DIRECTION_INDEXES = mapOf(
    "LEFT" to listOf(LEFT), "RIGHT" to listOf(RIGHT), "UP" to listOf(UP), "DOWN" to listOf(DOWN),
    "HORIZONTAL" to listOf(LEFT, RIGHT), "VERTICAL" to listOf(UP, DOWN),
)
