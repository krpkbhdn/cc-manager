package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val STEP_MILLIS = 12L
private const val SETTLE_MILLIS = 900L
private const val DRAG_STEPS = 40
private const val TITLE_OFFSET = 14
private const val DOUBLE_CLICK_GAP_MILLIS = 80L

/** `--snap`: drives the native title bar with real mouse input and reports snap, drag-restore, double-click and resize lock. */
internal suspend fun windowProbe(window: Window, windowState: WindowState, capture: Capture, report: Path) {
    val probe = WindowProbe(window, windowState, capture)
    probe.run()
    withContext(Dispatchers.IO) { Files.writeString(report, probe.results.joinToString("\n")) }
}

private class WindowProbe(private val window: Window, private val windowState: WindowState, private val capture: Capture) {
    val results = mutableListOf<String>()
    private val robot = Robot()

    suspend fun run() {
        window.toFront()
        delay(SETTLE_MILLIS)
        val floating = window.bounds
        check("starts floating", windowState.placement == WindowPlacement.Floating)
        dragTitle(from = titlePoint(), to = (window.x + window.width / 2) to 0)
        check("dragging the title to the top edge maximizes (Aero Snap)", windowState.placement == WindowPlacement.Maximized)
        capture.take("snap-maximized")
        val maximized = window.bounds
        dragTitle(from = (maximized.x + maximized.width / 2) to (maximized.y + TITLE_OFFSET), to = (maximized.width / 2) to 420)
        check("dragging the maximized title down restores the window", windowState.placement == WindowPlacement.Floating)
        check("restored window follows the cursor, not the maximized frame", window.bounds.y > 100)
        capture.take("restored-by-drag")
        doubleClickTitle()
        check("double-clicking the title maximizes", windowState.placement == WindowPlacement.Maximized)
        resizeAttempt()
        check("a maximized window cannot be resized", !resizedAfterAttempt)
        doubleClickTitle()
        check("double-clicking again restores", windowState.placement == WindowPlacement.Floating)
        check("restored size is the remembered floating size", window.width == floating.width && window.height == floating.height)
    }

    private fun titlePoint() = (window.x + window.width / 2 + TITLE_OFFSET * 4) to (window.y + TITLE_OFFSET)

    private suspend fun dragTitle(from: Pair<Int, Int>, to: Pair<Int, Int>) {
        withContext(Dispatchers.IO) {
            robot.mouseMove(from.first, from.second)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            for (step in 1..DRAG_STEPS) {
                robot.mouseMove(from.first + (to.first - from.first) * step / DRAG_STEPS, from.second + (to.second - from.second) * step / DRAG_STEPS)
                Thread.sleep(STEP_MILLIS)
            }
            Thread.sleep(SETTLE_MILLIS / 2)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        }
        delay(SETTLE_MILLIS)
    }

    private suspend fun doubleClickTitle() {
        val bounds = window.bounds
        withContext(Dispatchers.IO) {
            robot.mouseMove(bounds.x + bounds.width / 2 + TITLE_OFFSET * 4, bounds.y + TITLE_OFFSET)
            repeat(2) {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                Thread.sleep(DOUBLE_CLICK_GAP_MILLIS)
            }
        }
        delay(SETTLE_MILLIS)
    }

    private suspend fun resizeAttempt() {
        val before = window.bounds
        withContext(Dispatchers.IO) {
            robot.mouseMove(before.x + before.width / 2, before.y + before.height - 1)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseMove(before.x + before.width / 2, before.y + before.height - 200)
            Thread.sleep(SETTLE_MILLIS / 2)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        }
        delay(SETTLE_MILLIS)
        results += "      (maximized bounds before=$before after=${window.bounds})"
        resizedAfterAttempt = window.bounds != before
    }

    private var resizedAfterAttempt = false

    private fun check(name: String, ok: Boolean) {
        results += "${if (ok) "PASS" else "FAIL"}  $name"
        println(results.last())
    }
}
