package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.concurrent.thread

private const val SETTLE_MILLIS = 3000L
private const val STEP_MILLIS = 450L
private const val WINDOW_X = 100
private const val WINDOW_Y = 60
private const val WINDOW_W = 1000
private const val WINDOW_H = 700
private const val HEADER_X = 400
private const val HEADER_Y = 85
private const val EYE_X = 930
private const val TOOLBAR_Y = 156
private const val FILTER_X = 600
private const val FILTER_Y = 215
private const val NEW_FOLDER_X = 676
private const val FOOTER_Y = 638
private const val HOME_X = 100
private const val HOME_Y = 370
private val sandbox =Path.of("C:\\Users\\Bohdan\\Work\\Code\\Personal\\ccm-sandbox")

/** Dev-only: `DevPickerHarness <outDir> <dark|light>` drives the real dialog with Robot and writes PNG screenshots. */
fun main(args: Array<String>) {
    val outDir = File(args[0]).apply { mkdirs() }
    val dark = args.getOrNull(1) != "light"
    val recents = RecentFolders(File(outDir, "recent.txt").toPath())
    recents.add(sandbox.resolve("demo-api"))
    recents.add(sandbox.resolve("alpha-service"))
    application {
        var picked by mutableStateOf<Path?>(null)
        var dismissed by mutableStateOf(false)
        Window(
            onCloseRequest = ::exitApplication,
            alwaysOnTop = true,
            state = rememberWindowState(width = WINDOW_W.dp, height = WINDOW_H.dp, position = WindowPosition(WINDOW_X.dp, WINDOW_Y.dp)),
        ) {
            AppTheme(dark) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    Label("Host app behind the dialog", Modifier.background(colors.background), color = colors.textSecondary)
                    if (!dismissed && picked == null) FolderPicker(sandbox, recents, { dismissed = true }, { picked = it })
                    picked?.let { Label("PICKED: $it") }
                }
            }
            androidx.compose.runtime.remember {
                thread(isDaemon = true) { guarded(outDir) { Script(window, outDir, args.getOrNull(2) ?: "all").run() } }
            }
        }
    }
}

private fun guarded(outDir: File, block: () -> Unit) = try { block() } catch (e: Throwable) {
    File(outDir, "error.txt").writeText(e.stackTraceToString())
} finally { Runtime.getRuntime().halt(0) }

private class Script(private val window: java.awt.Window, private val outDir: File, private val scenario: String) {
    private val bounds: Rectangle get() = window.bounds
    private val robot = Robot().apply { autoDelay = 30 }

    fun run() {
        Thread.sleep(SETTLE_MILLIS)
        shot("01-open")
        click(HEADER_X, HEADER_Y)
        repeat(3) { press(KeyEvent.VK_DOWN) }
        shot("02-keyboard-selection")
        press(KeyEvent.VK_ENTER)
        shot("03-only-hidden")
        click(EYE_X, TOOLBAR_Y)
        shot("04-show-hidden")
        alt(KeyEvent.VK_LEFT)
        typeLetters("zeta")
        shot("05-typeahead")
        press(KeyEvent.VK_ENTER)
        shot("06-nested")
        press(KeyEvent.VK_BACK_SPACE)
        shot("07-up-reselects")
        ctrl(KeyEvent.VK_L)
        paste(sandbox.resolve("zeta-tools").toString() + File.separator + "ne", select = true)
        shot("08-suggestions")
        press(KeyEvent.VK_ESCAPE)
        ctrl(KeyEvent.VK_L)
        paste("C:\\no\\such\\folder", select = true)
        shot("09-invalid")
        press(KeyEvent.VK_ESCAPE)
        click(FILTER_X, FILTER_Y)
        typeLetters("de")
        shot("10-filter")
        press(KeyEvent.VK_ESCAPE)
        click(NEW_FOLDER_X, FOOTER_Y)
        paste("bad:name", select = true)
        shot("11-new-folder-error")
        paste("created-by-test", select = true)
        press(KeyEvent.VK_ENTER)
        shot("12-new-folder-created")
        click(HOME_X, HOME_Y)
        shot("13-home")
        ctrl(KeyEvent.VK_L)
        paste(sandbox.toString(), select = true)
        press(KeyEvent.VK_ENTER)
        shot("14-typed-path-jump")
        ctrl(KeyEvent.VK_ENTER)
        shot("15-picked")
        Runtime.getRuntime().halt(0)
    }

    private fun shot(name: String) {
        Thread.sleep(STEP_MILLIS)
        ImageIO.write(robot.createScreenCapture(Rectangle(bounds)), "png", File(outDir, "$name.png"))
    }

    private fun press(code: Int) { robot.keyPress(code); robot.keyRelease(code); Thread.sleep(STEP_MILLIS / 3) }

    private fun alt(code: Int) { robot.keyPress(KeyEvent.VK_ALT); press(code); robot.keyRelease(KeyEvent.VK_ALT) }

    private fun ctrl(code: Int) { robot.keyPress(KeyEvent.VK_CONTROL); press(code); robot.keyRelease(KeyEvent.VK_CONTROL) }

    private fun typeLetters(text: String) = text.forEach { press(KeyEvent.getExtendedKeyCodeForChar(it.code)) }

    private fun paste(text: String, select: Boolean = false) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        if (select) ctrl(KeyEvent.VK_A)
        ctrl(KeyEvent.VK_V)
    }

    private fun click(x: Int, y: Int) {
        robot.mouseMove(bounds.x + x, bounds.y + y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }
}
