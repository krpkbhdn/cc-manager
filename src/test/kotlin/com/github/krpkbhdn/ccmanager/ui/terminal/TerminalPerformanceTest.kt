package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Test
import java.io.File

private const val ESC = "\u001b"
private const val COLUMNS = 120
private const val ROWS = 40
private const val CELL_WIDTH = 8
private const val PADDING_X = 12
private const val PADDING_Y = 8
private const val WARMUP_FRAMES = 60
private const val MEASURED_FRAMES = 300
private const val NANOS_PER_MILLI = 1_000_000.0
private const val FRAME_BUDGET_MILLIS = 16.0

/** A busy 120x40 screen like Claude streaming a reply: how long does one frame of painting take. */
class TerminalPerformanceTest {

    private fun busyScreen(): String = (1..ROWS).joinToString("\r\n") { row ->
        val colored = "${ESC}[38;2;215;119;87m●${ESC}[0m ${ESC}[1mrow $row${ESC}[0m ${ESC}[38;5;${16 + row}mcolored text with 漢字 and symbols ⏵⏵ ❯ ✻ ✓${ESC}[0m"
        colored + " ${ESC}[48;5;238m padded ${ESC}[0m " + "lorem ipsum dolor sit amet ".repeat(3).take(COLUMNS - 70)
    }

    @Test
    fun paintingAFullBusyScreenFitsTheFrameBudget() {
        val connector = ScriptedConnector()
        val session = TerminalSession(connector)
        session.fit(COLUMNS, ROWS)
        connector.feed("${ESC}[2J${ESC}[H" + busyScreen())
        Thread.sleep(600)
        settle(connector, session)
        val fonts = TerminalFonts(CELL_WIDTH)
        val painter = TerminalPainter(fonts, PADDING_X, PADDING_Y)
        val width = COLUMNS * CELL_WIDTH + 2 * PADDING_X
        val height = ROWS * fonts.cellHeight + 2 * PADDING_Y
        val surface = Surface.makeRasterN32Premul(width, height)
        repeat(WARMUP_FRAMES) { painter.paint(surface.canvas, width, height, session, true) }
        val millis = DoubleArray(MEASURED_FRAMES) {
            val start = System.nanoTime()
            painter.paint(surface.canvas, width, height, session, true)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }.sorted()
        val average = millis.average()
        val p95 = millis[(MEASURED_FRAMES * 95) / 100]
        val report = "120x40 paint: avg %.2f ms, p95 %.2f ms, max %.2f ms over %d frames".format(average, p95, millis.last(), MEASURED_FRAMES)
        println("PERF $report")
        File(shotDirectory(), "perf.txt").apply { parentFile.mkdirs() }.writeText(report)
        session.close()
        assertThat(average).isLessThan(FRAME_BUDGET_MILLIS / 2)
    }

    @Test
    fun streamingOutputWhilePaintingKeepsUp() {
        val connector = ScriptedConnector()
        val session = TerminalSession(connector)
        session.fit(COLUMNS, ROWS)
        val fonts = TerminalFonts(CELL_WIDTH)
        val painter = TerminalPainter(fonts, PADDING_X, PADDING_Y)
        val width = COLUMNS * CELL_WIDTH + 2 * PADDING_X
        val height = ROWS * fonts.cellHeight + 2 * PADDING_Y
        val surface = Surface.makeRasterN32Premul(width, height)
        val chunk = busyScreen()
        val millis = ArrayList<Double>()
        repeat(MEASURED_FRAMES) {
            connector.feed("${ESC}[H$chunk")
            val start = System.nanoTime()
            painter.paint(surface.canvas, width, height, session, true)
            millis += (System.nanoTime() - start) / NANOS_PER_MILLI
            Thread.sleep(2)
        }
        val sorted = millis.drop(WARMUP_FRAMES).sorted()
        val report = "120x40 paint while streaming a full redraw per frame: avg %.2f ms, p95 %.2f ms, max %.2f ms".format(sorted.average(), sorted[sorted.size * 95 / 100], sorted.last())
        println("PERF $report")
        File(shotDirectory(), "perf-streaming.txt").apply { parentFile.mkdirs() }.writeText(report)
        session.close()
        assertThat(sorted.average()).isLessThan(FRAME_BUDGET_MILLIS)
    }
}
