package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.compatibility.Point
import com.jediterm.terminal.model.SelectionUtil
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils

private const val CLICKS_FOR_WORD = 2
private const val CLICKS_FOR_LINE = 3
private val WORD_SEPARATORS = " \t\"'`()[]{}<>,;|"

/** A position in the buffer: [line] counts from the first screen line (0), scrollback lines are negative. */
internal data class Cell(val column: Int, val line: Int) : Comparable<Cell> {
    override fun compareTo(other: Cell): Int = compareValuesBy(this, other, Cell::line, Cell::column)
}

/** Text selected between two cells, both inclusive; [anchor] stays where the gesture started. */
internal data class CellSelection(val anchor: Cell, val head: Cell) {
    val start: Cell get() = minOf(anchor, head)

    val end: Cell get() = maxOf(anchor, head)

    /** Selected columns of [line] in a terminal [width] cells wide, or null when the line is outside the selection. */
    fun columnsIn(line: Int, width: Int): IntRange? {
        if (line < start.line || line > end.line) return null
        val first = if (line == start.line) start.column else 0
        val last = if (line == end.line) end.column else width - 1
        return (first..minOf(last, width - 1)).takeUnless { it.isEmpty() }
    }

    fun extendedTo(cell: Cell) = copy(head = cell)

    /** Copied text: wrapped lines are joined, every line loses its trailing blanks. */
    fun textIn(buffer: TerminalTextBuffer): String {
        val raw = buffer.withLock { SelectionUtil.getSelectionText(Point(start.column, start.line), Point(end.column + 1, end.line), buffer) }
        return raw.lines().joinToString("\n") { it.trimEnd() }.trimEnd('\n')
    }

    companion object {
        fun of(cell: Cell, clicks: Int, lineText: String, width: Int): CellSelection = when {
            clicks >= CLICKS_FOR_LINE -> CellSelection(Cell(0, cell.line), Cell(width - 1, cell.line))
            clicks == CLICKS_FOR_WORD -> wordBounds(lineText, cell.column).let { CellSelection(Cell(it.first, cell.line), Cell(it.last, cell.line)) }
            else -> CellSelection(cell, cell)
        }
    }
}

internal inline fun <T> TerminalTextBuffer.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/** Columns of the word under [column]; a word is a run of characters that are neither blanks nor brackets/quotes. */
internal fun wordBounds(lineText: String, column: Int): IntRange {
    if (lineText.isEmpty()) return column..column
    var index = column.coerceIn(0, lineText.lastIndex)
    if (lineText[index] == CharUtils.DWC && index > 0) index--
    if (isSeparator(lineText[index])) return index..index
    var first = index
    while (first > 0 && !isSeparator(lineText[first - 1])) first--
    var last = index
    while (last < lineText.lastIndex && !isSeparator(lineText[last + 1])) last++
    return first..last
}

private fun isSeparator(char: Char) = char in WORD_SEPARATORS || char == CharUtils.NUL_CHAR

/** Counts consecutive clicks on the same cell that follow each other within [intervalMillis]. */
internal class ClickCounter(private val intervalMillis: Long = DOUBLE_CLICK_MILLIS) {
    private var lastCell: Cell? = null
    private var lastMillis = 0L
    private var count = 0

    fun register(cell: Cell, timeMillis: Long): Int {
        val repeated = cell == lastCell && timeMillis - lastMillis <= intervalMillis
        count = if (repeated) count + 1 else 1
        lastCell = cell
        lastMillis = timeMillis
        return count
    }

    private companion object {
        const val DOUBLE_CLICK_MILLIS = 400L
    }
}
