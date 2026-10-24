package com.github.krpkbhdn.ccmanager.ui.state

import kotlin.math.max
import kotlin.math.min

/** Usable area of one screen in the same coordinate space as [WindowBounds]. */
data class ScreenArea(val x: Int, val y: Int, val width: Int, val height: Int)

private const val MIN_VISIBLE_WIDTH = 160
private const val MIN_VISIBLE_HEIGHT = 80

/** Fits remembered [bounds] onto [screens]; a window that would be (nearly) off-screen loses its position so the OS places it. */
fun fitToScreens(bounds: WindowBounds, screens: List<ScreenArea>): WindowBounds {
    val primary = screens.firstOrNull() ?: return bounds
    val x = bounds.x
    val y = bounds.y
    if (x == null || y == null) return bounds.copy(width = min(bounds.width, primary.width), height = min(bounds.height, primary.height))
    val target = screens.maxBy { overlap(it, x, y, bounds.width, bounds.height) }
    val visible = overlapSize(target, x, y, bounds.width, bounds.height)
    if (visible.first < MIN_VISIBLE_WIDTH || visible.second < MIN_VISIBLE_HEIGHT) {
        return bounds.copy(width = min(bounds.width, primary.width), height = min(bounds.height, primary.height), x = null, y = null)
    }
    val width = min(bounds.width, target.width)
    val height = min(bounds.height, target.height)
    return bounds.copy(
        width = width,
        height = height,
        x = x.coerceIn(target.x, target.x + target.width - width),
        y = y.coerceIn(target.y, target.y + target.height - height),
    )
}

private fun overlap(screen: ScreenArea, x: Int, y: Int, width: Int, height: Int): Long =
    overlapSize(screen, x, y, width, height).let { it.first.toLong() * it.second }

private fun overlapSize(screen: ScreenArea, x: Int, y: Int, width: Int, height: Int): Pair<Int, Int> =
    max(0, min(x + width, screen.x + screen.width) - max(x, screen.x)) to max(0, min(y + height, screen.y + screen.height) - max(y, screen.y))
