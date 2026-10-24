package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.jediterm.terminal.util.CharUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TerminalGridTest {
    private val geometry = TerminalGeometry(cellWidth = 8, cellHeight = 18, paddingX = 12, paddingY = 8)

    @Test
    fun gridFitsWholeCellsInsideThePadding() {
        assertThat(geometry.gridFor(12 + 120 * 8 + 12, 8 + 32 * 18 + 8)).isEqualTo(Pair(120, 32))
        assertThat(geometry.gridFor(12 + 120 * 8 + 12 + 7, 8 + 32 * 18 + 8 + 17)).isEqualTo(Pair(120, 32))
        assertThat(geometry.gridFor(10, 10)).isEqualTo(Pair(2, 2))
    }

    @Test
    fun pointsMapToCellsAndStayInsideTheGrid() {
        assertThat(geometry.gridCellAt(Offset(12f, 8f), 120, 32)).isEqualTo(Pair(0, 0))
        assertThat(geometry.gridCellAt(Offset(12f + 8 * 5 + 7, 8f + 18 * 3), 120, 32)).isEqualTo(Pair(5, 3))
        assertThat(geometry.gridCellAt(Offset(-50f, 9999f), 120, 32)).isEqualTo(Pair(0, 31))
    }

    @Test
    fun scrolledViewMapsRowsToScrollbackLines() {
        val cell = geometry.bufferCellAt(Offset(12f + 8 * 2, 8f + 18 * 4), scroll = 10f, columns = 120, rows = 32, scrollback = 100)
        assertThat(cell).isEqualTo(Cell(2, -6))
        assertThat(geometry.bufferCellAt(Offset(0f, -500f), 100f, 120, 32, 100).line).isEqualTo(-100)
    }

    @Test
    fun selectionCoversTheRightColumnsPerLine() {
        val selection = CellSelection(Cell(5, 3), Cell(2, 1))
        assertThat(selection.start).isEqualTo(Cell(2, 1))
        assertThat(selection.columnsIn(0, 10)).isNull()
        assertThat(selection.columnsIn(1, 10)).isEqualTo(2..9)
        assertThat(selection.columnsIn(2, 10)).isEqualTo(0..9)
        assertThat(selection.columnsIn(3, 10)).isEqualTo(0..5)
        assertThat(selection.columnsIn(4, 10)).isNull()
        assertThat(CellSelection(Cell(3, 0), Cell(7, 0)).columnsIn(0, 10)).isEqualTo(3..7)
        assertThat(CellSelection(Cell(3, 0), Cell(30, 0)).columnsIn(0, 10)).isEqualTo(3..9)
    }

    @Test
    fun wordSelectionStopsAtBlanksAndBrackets() {
        val text = "run (src/main.kt) now"
        assertThat(wordBounds(text, 6)).isEqualTo(5..15)
        assertThat(wordBounds(text, 0)).isEqualTo(0..2)
        assertThat(wordBounds(text, 3)).isEqualTo(3..3)
        assertThat(wordBounds("漢" + CharUtils.DWC + "字" + CharUtils.DWC, 1)).isEqualTo(0..3)
    }

    @Test
    fun clicksCountOnlyWhenRepeatedQuicklyOnTheSameCell() {
        val counter = ClickCounter(intervalMillis = 400)
        assertThat(counter.register(Cell(1, 1), 0)).isEqualTo(1)
        assertThat(counter.register(Cell(1, 1), 200)).isEqualTo(2)
        assertThat(counter.register(Cell(1, 1), 350)).isEqualTo(3)
        assertThat(counter.register(Cell(1, 1), 1000)).isEqualTo(1)
        assertThat(counter.register(Cell(2, 1), 1100)).isEqualTo(1)
    }

    @Test
    fun lineAndWordSelectionsComeFromTheClickCount() {
        assertThat(CellSelection.of(Cell(4, 2), 1, "hello world", 80)).isEqualTo(CellSelection(Cell(4, 2), Cell(4, 2)))
        assertThat(CellSelection.of(Cell(7, 2), 2, "hello world", 80)).isEqualTo(CellSelection(Cell(6, 2), Cell(10, 2)))
        assertThat(CellSelection.of(Cell(7, 2), 3, "hello world", 80)).isEqualTo(CellSelection(Cell(0, 2), Cell(79, 2)))
    }

    @Test
    fun runsAreSplitAtWideCharacterBoundaries() {
        val paint = CellPaint(Color.White, Color.Black)
        val text = "ab" + "漢" + CharUtils.DWC + "😀" + "z"
        val runs = restyled(listOf(Run(0, text, paint)), 3..3) { it.copy(background = Color.Red) }
        assertThat(runs.map { it.text }).containsExactly("ab", "漢" + CharUtils.DWC, "😀z")
        val inEmoji = restyled(listOf(Run(0, text, paint)), 5..5) { it.copy(background = Color.Red) }
        assertThat(inEmoji.map { it.text }).containsExactly("ab漢" + CharUtils.DWC, "😀", "z")
        assertThat(inEmoji.map { it.column }).containsExactly(0, 4, 6)
    }

    @Test
    fun clustersKnowTheirCellWidth() {
        val clusters = clustersOf("a漢" + CharUtils.DWC + "😀⏵")
        assertThat(clusters.map { it.cells }).containsExactly(1, 2, 2, 1)
        assertThat(clusters.map { it.codePoint }).containsExactly('a'.code, '漢'.code, 0x1F600, '⏵'.code)
    }
}
