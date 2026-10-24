package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor

private const val MIN_GRID_SIZE = 2

/** Where the cell grid sits in the view: padding around it and the size of one cell, all in pixels. */
internal data class TerminalGeometry(val cellWidth: Int, val cellHeight: Int, val paddingX: Int, val paddingY: Int) {
    /** How many columns and rows fit into a view of the given pixel size. */
    fun gridFor(width: Int, height: Int): Pair<Int, Int> = Pair(
        ((width - 2 * paddingX) / cellWidth).coerceAtLeast(MIN_GRID_SIZE),
        ((height - 2 * paddingY) / cellHeight).coerceAtLeast(MIN_GRID_SIZE),
    )

    /** Visible grid cell under [point], for reporting to an application; clamped to the grid. */
    fun gridCellAt(point: Offset, columns: Int, rows: Int): Pair<Int, Int> = Pair(
        floor((point.x - paddingX) / cellWidth).toInt().coerceIn(0, columns - 1),
        floor((point.y - paddingY) / cellHeight).toInt().coerceIn(0, rows - 1),
    )

    /** Buffer cell under [point] when the view is scrolled up by [scroll] lines; the line is clamped to the buffer. */
    fun bufferCellAt(point: Offset, scroll: Float, columns: Int, rows: Int, scrollback: Int): Cell = Cell(
        floor((point.x - paddingX) / cellWidth).toInt().coerceIn(0, columns - 1),
        floor((point.y - paddingY) / cellHeight - scroll).toInt().coerceIn(-scrollback, rows - 1),
    )
}
