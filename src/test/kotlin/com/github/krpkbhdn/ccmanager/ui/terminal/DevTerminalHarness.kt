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
import java.io.File
import javax.imageio.ImageIO
import kotlin.concurrent.thread

private const val SETTLE_MILLIS = 6000L
private const val SHORT_MILLIS = 1500L

/**
 * Dev-only: `DevTerminalHarness <outDir> <shell|claude> [light]` opens a window with TerminalView, toggles the view
 * off and on to prove re-attachment, and writes PNG screenshots to outDir.
 */
fun main(args: Array<String>) {
    val outDir = File(args[0]).apply { mkdirs() }
    val command = if (args[1] == "claude") listOf(File(System.getProperty("user.home"), ".local/bin/claude.exe").path)
    else listOf("powershell.exe", "-NoLogo")
    val dark = args.getOrNull(2) != "light"
    val process = PtyProcessBuilder(command.toTypedArray()).setDirectory(System.getProperty("java.io.tmpdir"))
        .setEnvironment(System.getenv() + mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor"))
        .setInitialColumns(120).setInitialRows(32).start()
    val session = LaunchedSession("dev", 1, "dev", process)
    TerminalHost.open(session)

    application {
        var visible by mutableStateOf(true)
        Window(
            onCloseRequest = ::exitApplication,
            alwaysOnTop = true,
            state = rememberWindowState(width = 1100.dp, height = 700.dp, position = WindowPosition(100.dp, 100.dp)),
        ) {
            if (visible) TerminalView("dev", Modifier.fillMaxSize(), dark)
            remember { thread(isDaemon = true) { script(window, outDir, args[1], { visible = it }) } }
        }
    }
}

private fun script(window: java.awt.Window, outDir: File, kind: String, setVisible: (Boolean) -> Unit) = try { runScript(window, outDir, kind, setVisible) } catch (e: Throwable) {
    File(outDir, "error.txt").writeText(e.stackTraceToString())
} finally { Runtime.getRuntime().halt(0) }

private fun runScript(window: java.awt.Window, outDir: File, kind: String, setVisible: (Boolean) -> Unit) {
    Thread.sleep(SETTLE_MILLIS)
    if (kind == "shell") {
        TerminalHost.send("dev", "Clear-Host; Write-Host 'Привіт 日本語 😀 █▓' -ForegroundColor Green; Get-ChildItem | Select-Object -First 6\r")
        Thread.sleep(SHORT_MILLIS)
    }
    shot(window.bounds, File(outDir, "1-initial.png"))
    setVisible(false)
    Thread.sleep(SHORT_MILLIS)
    setVisible(true)
    Thread.sleep(SHORT_MILLIS * 2)
    shot(window.bounds, File(outDir, "2-reattached.png"))
    TerminalHost.close("dev")
    Thread.sleep(SHORT_MILLIS)
    Runtime.getRuntime().halt(0)
}

private fun shot(bounds: Rectangle, file: File) {
    ImageIO.write(Robot().createScreenCapture(bounds), "png", file)
}
