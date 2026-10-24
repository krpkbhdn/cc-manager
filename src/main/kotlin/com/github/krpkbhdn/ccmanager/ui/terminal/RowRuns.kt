package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.util.CharUtils

private const val DIM_FOREGROUND_SHARE = 0.6f

/** How the cells of a run look once colors, inverse, dim and hidden are applied. */
internal data class CellPaint(
    val foreground: Color,
    val background: Color,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    val styleBits: Int get() = (if (bold) BundledTypefaces.STYLE_BOLD else 0) or (if (italic) BundledTypefaces.STYLE_ITALIC else 0)
}

/**
 * Cells of one line sharing a [paint], starting at [column]. Each char of [text] is one cell: a wide BMP character is
 * followed by [CharUtils.DWC] and a surrogate pair (emoji) spans two cells.
 */
internal data class Run(val column: Int, val text: String, val paint: CellPaint) {
    val end: Int get() = column + text.length
}

internal fun paintOf(style: TextStyle, theme: TerminalTheme): CellPaint {
    var foreground = style.foreground?.let(theme::resolve) ?: theme.foreground
    var background = style.background?.let(theme::resolve) ?: theme.background
    if (style.hasOption(TextStyle.Option.INVERSE)) foreground = background.also { background = foreground }
    if (style.hasOption(TextStyle.Option.DIM)) foreground = lerp(background, foreground, DIM_FOREGROUND_SHARE)
    if (style.hasOption(TextStyle.Option.HIDDEN)) foreground = background
    return CellPaint(
        foreground = foreground,
        background = background,
        bold = style.hasOption(TextStyle.Option.BOLD),
        italic = style.hasOption(TextStyle.Option.ITALIC),
        underline = style.hasOption(TextStyle.Option.UNDERLINED),
    )
}

/** The runs of [line], neighbours with the same look merged; empty cells past the text are left out. */
internal fun runsOf(line: TerminalLine, theme: TerminalTheme): List<Run> {
    val runs = ArrayList<Run>()
    var column = 0
    for (entry in line.entries) {
        val text = entry.text.toString().replace(CharUtils.NUL_CHAR, CharUtils.EMPTY_CHAR)
        val paint = paintOf(entry.style, theme)
        val last = runs.lastOrNull()
        if (last != null && last.paint == paint) runs[runs.lastIndex] = last.copy(text = last.text + text) else runs += Run(column, text, paint)
        column += text.length
    }
    return runs
}

/** Splits [runs] so that the cells in [columns] can get another look from [restyle]; cuts never fall inside a wide character. */
internal fun restyled(runs: List<Run>, columns: IntRange, restyle: (CellPaint) -> CellPaint): List<Run> {
    val result = ArrayList<Run>(runs.size + 2)
    for (run in runs) {
        val from = maxOf(columns.first, run.column) - run.column
        val to = minOf(columns.last + 1, run.end) - run.column
        if (from >= to) {
            result += run
            continue
        }
        val cutFrom = clusterStart(run.text, from)
        val cutTo = clusterEnd(run.text, to)
        if (cutFrom > 0) result += Run(run.column, run.text.substring(0, cutFrom), run.paint)
        result += Run(run.column + cutFrom, run.text.substring(cutFrom, cutTo), restyle(run.paint))
        if (cutTo < run.text.length) result += Run(run.column + cutTo, run.text.substring(cutTo), run.paint)
    }
    return result
}

private fun isContinuation(char: Char) = char == CharUtils.DWC || char.isLowSurrogate()

private fun clusterStart(text: String, index: Int): Int = if (index in 1 until text.length && isContinuation(text[index])) index - 1 else index

private fun clusterEnd(text: String, index: Int): Int = if (index in 1 until text.length && isContinuation(text[index])) index + 1 else index

/** A character of a run with the number of cells it covers. */
internal data class Cluster(val offset: Int, val codePoint: Int, val cells: Int)

/** Splits run text into drawable characters; the filler of a wide character is not one of its own. */
internal fun clustersOf(text: String): List<Cluster> {
    val clusters = ArrayList<Cluster>(text.length)
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == CharUtils.DWC) {
            index++
            continue
        }
        val codePoint = text.codePointAt(index)
        val chars = Character.charCount(codePoint)
        val filler = if (index + chars < text.length && text[index + chars] == CharUtils.DWC) 1 else 0
        clusters += Cluster(index, codePoint, chars + filler)
        index += chars + filler
    }
    return clusters
}
