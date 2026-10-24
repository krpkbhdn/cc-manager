package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.Dialog
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

private const val SETTLE_MILLIS = 3000L
private const val STEP_MILLIS = 600L
private const val DRAG_STEPS = 12
private const val TITLE_BAR_OFFSET = 14
private const val EDGE_INSET = 2
private val sandbox = Path.of("C:\\Users\\Bohdan\\Work\\Code\\Personal\\ccm-sandbox")

/** Dev-only: `DevPickerHarness <outDir> <dark|light>` drives the real dialog window with Robot (move, resize, modality) and writes PNG screenshots plus report.txt. */
fun main(args: Array<String>) {
    val outDir = File(args[0]).apply { mkdirs() }
    val dark = args.getOrNull(1) != "light"
    val recents = RecentFolders(File(outDir, "recent.txt").toPath()).apply { add(sandbox.resolve("demo-api")) }
    val store = PickerWindowStore(File(outDir, "picker-window.txt").toPath())
    application {
        var dismissed by mutableStateOf(false)
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 700.dp, height = 500.dp, position = WindowPosition(60.dp, 60.dp)),
        ) {
            AppTheme(dark) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    Label("Host app behind the dialog", color = colors.textSecondary)
                    if (!dismissed) FolderPicker(sandbox, recents, store, { dismissed = true }, { dismissed = true })
                }
            }
            remember { thread(isDaemon = true) { guarded(outDir) { Script(window, outDir, store).run() } } }
        }
    }
}

private fun guarded(outDir: File, block: () -> Unit) = try { block() } catch (e: Throwable) {
    File(outDir, "error.txt").writeText(e.stackTraceToString())
} finally { Runtime.getRuntime().halt(0) }

private class Script(private val host: java.awt.Window, private val outDir: File, private val store: PickerWindowStore) {
    private val robot = Robot().apply { autoDelay = 30 }
    private val report = StringBuilder()
    private val dialog: Dialog get() = java.awt.Window.getWindows().filterIsInstance<Dialog>().first { it.isShowing }
    private val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }

    fun run() {
        Thread.sleep(SETTLE_MILLIS)
        note("screens=$screens modal=${dialog.modalityType} title=${dialog.title} resizable=${dialog.isResizable} min=${dialog.minimumSize}")
        note("focusOwner=${dialog.focusOwner?.javaClass?.simpleName} active=${dialog.isActive}")
        onEdt { dialog.isAlwaysOnTop = true }
        shot("01-default")
        onEdt { dialog.setLocation(300, 200) }
        shot("02-setLocation")
        onEdt { dialog.setSize(560, 420) }
        shot("03-min-size")
        onEdt { dialog.setSize(1300, 900) }
        shot("04-large")
        onEdt { dialog.setBounds(200, 150, 860, 460) }
        Thread.sleep(STEP_MILLIS)
        robot.mouseMove(dialog.x + 90, dialog.y + 380)
        repeat(8) { robot.mouseWheel(3); Thread.sleep(40) }
        shot("04b-rail-scrolled")
        onEdt { dialog.setBounds(200, 150, 860, 600) }
        Thread.sleep(STEP_MILLIS)
        val before = dialog.location
        drag(Point(dialog.x + dialog.width / 2, dialog.y + TITLE_BAR_OFFSET), Point(dialog.x + dialog.width / 2 + 220, dialog.y + TITLE_BAR_OFFSET + 90))
        note("titleDrag $before -> ${dialog.location}")
        shot("05-dragged-by-title")
        val sizeBefore = dialog.size
        drag(Point(dialog.x + dialog.width - EDGE_INSET, dialog.y + dialog.height - EDGE_INSET), Point(dialog.x + dialog.width - 250, dialog.y + dialog.height - 150))
        note("cornerDrag $sizeBefore -> ${dialog.size}")
        shot("06-resized-by-corner")
        val sizeBefore2 = dialog.size
        drag(Point(dialog.x + dialog.width - EDGE_INSET, dialog.y + dialog.height / 2), Point(dialog.x + dialog.width + 150, dialog.y + dialog.height / 2))
        note("edgeDrag $sizeBefore2 -> ${dialog.size}")
        val screen = screens.first()
        onEdt { dialog.setBounds(screen.x + screen.width - 400, screen.y + screen.height - 300, 860, 600) }
        shot("07-partly-offscreen")
        note("offscreen bounds=${dialog.bounds}")
        onEdt { dialog.setBounds(150, 100, 860, 600) }
        Thread.sleep(STEP_MILLIS)
        robot.mouseMove(host.x + 20, host.y + 100)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        Thread.sleep(STEP_MILLIS)
        note("afterHostClick dialogShowing=${dialog.isShowing} dialogActive=${dialog.isActive} hostActive=${host.isActive}")
        shot("08-after-host-click")
        onEdt { dialog.setBounds(120, 90, 777, 555) }
        Thread.sleep(STEP_MILLIS * 2)
        note("storedBounds=${store.load()}")
        press(KeyEvent.VK_ESCAPE)
        Thread.sleep(STEP_MILLIS * 2)
        note("afterEsc dialogs=${java.awt.Window.getWindows().filterIsInstance<Dialog>().count { it.isShowing }} hostActive=${host.isActive} hostFocusOwner=${host.focusOwner?.javaClass?.simpleName}")
        File(outDir, "report.txt").writeText(report.toString())
    }

    private fun note(text: String) { report.appendLine(text); File(outDir, "report.txt").writeText(report.toString()) }

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun shot(name: String) {
        Thread.sleep(STEP_MILLIS)
        val b: Rectangle = dialog.bounds
        ImageIO.write(robot.createScreenCapture(Rectangle(b.x, b.y, b.width, b.height)), "png", File(outDir, "$name.png"))
    }

    private fun press(code: Int) { robot.keyPress(code); robot.keyRelease(code) }

    private fun drag(from: Point, to: Point) {
        robot.mouseMove(from.x, from.y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        repeat(DRAG_STEPS) { i ->
            robot.mouseMove(from.x + (to.x - from.x) * (i + 1) / DRAG_STEPS, from.y + (to.y - from.y) * (i + 1) / DRAG_STEPS)
            Thread.sleep(25)
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        Thread.sleep(STEP_MILLIS)
    }
}
