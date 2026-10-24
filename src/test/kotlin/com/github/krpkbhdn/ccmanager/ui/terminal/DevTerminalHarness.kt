package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.pty4j.PtyProcessBuilder
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread

private const val SETTLE_MILLIS = 6000L
private const val SHORT_MILLIS = 1500L

/**
 * Dev-only: `DevTerminalHarness <outDir> <glyphs|claude> [dark|light] [winpty]` opens a window with TerminalView on a
 * PowerShell glyph/color sheet or on the real claude in the sandbox, drives it with Robot and writes PNG screenshots
 * plus results.txt (pixel assertions) to outDir. claude consumes tokens only in the `claude` scenario (two prompts).
 */
fun main(args: Array<String>) {
    val outDir = File(args[0]).apply { mkdirs() }
    val scenario = args[1]
    val dark = args.getOrNull(2) != "light"
    val sheet = File(outDir, "sheet.ps1").apply { writeText("﻿" + if (scenario == "scroll") SCROLL_SCRIPT else GLYPH_SHEET_SCRIPT) }
    val command = if (scenario.startsWith("claude")) listOf(CLAUDE_EXE)
    else listOf("powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", sheet.path)
    val process = PtyProcessBuilder(command.toTypedArray())
        .setDirectory(if (scenario.startsWith("claude")) SANDBOX_DIR else System.getProperty("java.io.tmpdir"))
        .setUseWinConPty(args.getOrNull(3) != "winpty")
        .setEnvironment(cleanDevEnvironment())
        .setInitialColumns(120).setInitialRows(32).start()
    TerminalHost.open(LaunchedSession("dev", 1, "dev", process))

    application {
        var visible by mutableStateOf(true)
        Window(
            onCloseRequest = ::exitApplication,
            alwaysOnTop = true,
            state = rememberWindowState(width = 1100.dp, height = 700.dp, position = WindowPosition(100.dp, 100.dp)),
        ) {
            if (visible) TerminalView("dev", Modifier.fillMaxSize(), dark)
            remember { thread(isDaemon = true) { script(window, outDir, scenario) { visible = it } } }
        }
    }
}

private fun script(window: java.awt.Window, outDir: File, scenario: String, setVisible: (Boolean) -> Unit) = try {
    when {
        scenario.startsWith("claude") -> claudeScenario(window, outDir, promptsAllowed = scenario == "claude")
        scenario == "scroll" -> scrollScenario(window, outDir)
        else -> glyphScenario(window, outDir, setVisible)
    }
} catch (e: Throwable) {
    File(outDir, "error.txt").writeText(e.stackTraceToString())
} finally {
    Runtime.getRuntime().halt(0)
}

private fun glyphScenario(window: java.awt.Window, outDir: File, setVisible: (Boolean) -> Unit) {
    Thread.sleep(SETTLE_MILLIS)
    click(window)
    val first = shot(window.bounds, File(outDir, "1-glyphs.png"))
    File(outDir, "results.txt").writeText(EXPECTED_COLORS.joinToString("\n") { (name, rgb) ->
        "$name rgb=${"%06X".format(rgb)} exactPixels=${countExact(first, rgb)}"
    })
    ctrl(KeyEvent.VK_EQUALS)
    ctrl(KeyEvent.VK_EQUALS)
    Thread.sleep(SHORT_MILLIS)
    shot(window.bounds, File(outDir, "2-zoom-in.png"))
    ctrl(KeyEvent.VK_0)
    setVisible(false)
    Thread.sleep(SHORT_MILLIS)
    setVisible(true)
    Thread.sleep(SHORT_MILLIS * 2)
    shot(window.bounds, File(outDir, "3-reattached.png"))
    TerminalHost.close("dev")
}

private fun claudeScenario(window: java.awt.Window, outDir: File, promptsAllowed: Boolean) {
    Thread.sleep(SETTLE_MILLIS)
    val start = shot(window.bounds, File(outDir, "1-start.png"))
    File(outDir, "results.txt").writeText("claude logo orange D77757 exactPixels=${countExact(start, 0xD77757)}\n")
    for ((index, label) in listOf("manual", "accept-edits", "plan", "auto").withIndex()) {
        TerminalHost.send("dev", "\u001b[Z")
        Thread.sleep(SHORT_MILLIS)
        shot(window.bounds, File(outDir, "2-footer-${index + 1}-$label.png"))
    }
    TerminalHost.send("dev", "\u001b[Z")
    Thread.sleep(SHORT_MILLIS)
    if (!promptsAllowed) {
        click(window)
        type(KeyEvent.VK_A, KeyEvent.VK_B)
        type(KeyEvent.VK_ENTER, shift = true)
        type(KeyEvent.VK_C)
        Thread.sleep(SHORT_MILLIS)
        shot(window.bounds, File(outDir, "3-typed-shift-enter.png"))
        TerminalHost.close("dev")
        return
    }
    TerminalHost.send("dev", "say hi\r")
    Thread.sleep(2500)
    val spinner = shot(window.bounds, File(outDir, "3-spinner-a.png"))
    File(outDir, "results.txt").appendText("\nspinner orange D77757 exactPixels=${countExact(spinner, 0xD77757)}")
    Thread.sleep(700)
    shot(window.bounds, File(outDir, "3-spinner-b.png"))
    Thread.sleep(12000)
    shot(window.bounds, File(outDir, "4-reply.png"))
    TerminalHost.send("dev", "create a file x.txt containing hi\r")
    Thread.sleep(15000)
    shot(window.bounds, File(outDir, "5-permission.png"))
    TerminalHost.send("dev", "\u001b")
    Thread.sleep(SHORT_MILLIS * 2)
    shot(window.bounds, File(outDir, "6-denied.png"))
    TerminalHost.close("dev")
}

private fun ctrl(keyCode: Int) {
    val robot = Robot()
    robot.keyPress(KeyEvent.VK_CONTROL)
    robot.keyPress(keyCode)
    robot.keyRelease(keyCode)
    robot.keyRelease(KeyEvent.VK_CONTROL)
}

internal fun shot(bounds: Rectangle, file: File): BufferedImage =
    Robot().createScreenCapture(bounds).also { ImageIO.write(it, "png", file) }

private fun countExact(image: BufferedImage, rgb: Int): Int {
    var count = 0
    for (y in 0 until image.height) for (x in 0 until image.width) if (image.getRGB(x, y) and 0xFFFFFF == rgb) count++
    return count
}

/** Foreground block colors the sheet prints, as emitted by claude (truecolor) and by 256-color/ANSI programs. */
internal val EXPECTED_COLORS = listOf(
    "truecolor claude orange" to 0xD77757,
    "truecolor warning yellow" to 0xFFC107,
    "256-color 208" to 0xFF8700,
    "256-color 214" to 0xFFAF00,
    "256-color 209" to 0xFF875F,
    "256-color 216" to 0xFFAF87,
)

private val GLYPH_SHEET_SCRIPT = """
[Console]::OutputEncoding = [Text.Encoding]::UTF8
${'$'}e = [char]27
function B(${'$'}code) { "${'$'}e[${'$'}{code}m" + ([string][char]0x2588) * 6 + "${'$'}e[0m " }
[Console]::Out.Write("status: ⏵⏵ ⏸ ❯ ● ✻ ✢ ✽ ✶ · ⎿ ✓ ✗ ⚠ … ◐ ◑ ◒ ◓ ⏺ ✳ ✔ ✘ ★ ☐ ☑ ℹ`n")
[Console]::Out.Write("box: ╭────╮ ┌─┬─┐ ╔═══╗ ▐▛███▜▌ ░▒▓█ ▀▄▌▐ │ ├─┤ ╰────╯ └─┴─┘ ╚═══╝`n")
[Console]::Out.Write("braille: ⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏ ⣾⣽⣻⢿⡿⣟⣯⣷   arrows: ← ↑ → ↓ ↔ ⇧ ⌘ ⌥ ⏎ •‣◦ ▶ ▲ ▼ ◀`n")
[Console]::Out.Write("text: Привіт, Їжак ґанок Є 日本語 한국어 😀 🚀 ✅ ❌ 🔥 é ñ ü`n")
[Console]::Out.Write("styles: ${'$'}e[1mbold${'$'}e[0m ${'$'}e[2mdim${'$'}e[0m ${'$'}e[3mitalic${'$'}e[0m ${'$'}e[4munderline${'$'}e[0m ${'$'}e[7minverse${'$'}e[0m ${'$'}e[1;3mbold-italic${'$'}e[0m`n")
[Console]::Out.Write("ansi: ")
0..7 | ForEach-Object { [Console]::Out.Write((B "$(30 + ${'$'}_)")) }
[Console]::Out.Write("`n      ")
0..7 | ForEach-Object { [Console]::Out.Write((B "$(90 + ${'$'}_)")) }
[Console]::Out.Write("`n")
[Console]::Out.Write("rgb:  " + (B "38;2;215;119;87") + (B "38;2;255;193;7") + (B "38;5;208") + (B "38;5;214") + (B "38;5;209") + (B "38;5;216") + "`n")
[Console]::Out.Write("bg:   ${'$'}e[48;2;215;119;87m  claude  ${'$'}e[0m ${'$'}e[48;5;208m  208  ${'$'}e[0m ${'$'}e[41m  red  ${'$'}e[0m ${'$'}e[43m  yellow  ${'$'}e[0m`n")
Start-Sleep 60
""".trimIndent()

private fun click(window: java.awt.Window) {
    val robot = Robot()
    robot.mouseMove(window.x + window.width / 2, window.y + window.height / 2)
    robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
    robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
}

private fun type(vararg keyCodes: Int, shift: Boolean = false) {
    val robot = Robot()
    if (shift) robot.keyPress(KeyEvent.VK_SHIFT)
    keyCodes.forEach {
        robot.keyPress(it)
        robot.keyRelease(it)
        robot.delay(80)
    }
    if (shift) robot.keyRelease(KeyEvent.VK_SHIFT)
}
