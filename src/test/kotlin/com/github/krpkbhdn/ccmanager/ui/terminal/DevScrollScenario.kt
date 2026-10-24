package com.github.krpkbhdn.ccmanager.ui.terminal

import java.awt.Robot
import java.io.File

internal val SCROLL_SCRIPT = """
[Console]::OutputEncoding = [Text.Encoding]::UTF8
1..150 | ForEach-Object { 'line ' + ${'$'}_ + ' ⏵⏵ scrollback sample' }
Start-Sleep 60
""".trimIndent()

/** Fills the scrollback, wheel-scrolls and captures the scrollbar while visible and after it auto-hid. */
internal fun scrollScenario(window: java.awt.Window, outDir: File) {
    Thread.sleep(6000)
    shot(window.bounds, File(outDir, "1-bottom-idle.png"))
    val robot = Robot()
    robot.mouseMove(window.x + window.width / 2, window.y + window.height / 2)
    robot.mouseWheel(-6)
    Thread.sleep(300)
    shot(window.bounds, File(outDir, "2-scrolled-bar-visible.png"))
    Thread.sleep(3500)
    shot(window.bounds, File(outDir, "3-scrolled-bar-hidden.png"))
    TerminalHost.close("dev")
}
