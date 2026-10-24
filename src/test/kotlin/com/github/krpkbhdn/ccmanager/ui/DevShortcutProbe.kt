package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.terminal.TerminalHost
import com.github.krpkbhdn.ccmanager.ui.terminal.terminalHasFocus
import java.awt.KeyboardFocusManager
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val KEY_DELAY_MILLIS = 40L
private const val SETTLE_MILLIS = 350L
private const val BOOT_MILLIS = 9_000L
private const val MAX_ACTIVATION_TRIES = 5
private val sandboxApi = Path.of("C:/Users/Bohdan/Work/Code/Personal/ccm-sandbox/demo-api")

/** `--real --shortcuts`: drives the real window with java.awt.Robot while a real Claude terminal owns the keyboard focus. */
internal suspend fun shortcutProbe(capture: Capture, state: AppState, report: Path) {
    val probe = ShortcutProbe(capture, state)
    probe.run()
    withContext(Dispatchers.IO) { Files.writeString(report, probe.results.joinToString("\n")) }
}

private class ShortcutProbe(private val capture: Capture, private val state: AppState) {
    val results = mutableListOf<String>()
    private val robot = Robot()
    private val seen = java.util.concurrent.CopyOnWriteArrayList<String>()
    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
            if (e.id == KeyEvent.KEY_PRESSED) seen += "${KeyEvent.getKeyText(e.keyCode)}[c${e.isControlDown.compareTo(false)}s${e.isShiftDown.compareTo(false)}a${e.isAltDown.compareTo(false)}]"
            false
        }
    }
    private lateinit var firstSession: String
    private var clicked = false

    suspend fun run() {
        state.addProjectAt(sandboxApi)
        delay(1_200)
        val project = state.projects.first { it.name == "demo-api" }
        state.launch(project.id, LaunchMode.NEW)
        delay(BOOT_MILLIS)
        firstSession = state.live.of(project.id).first().ccmSessionId
        capture.window.toFront()
        state.terminal.focus(firstSession)
        delay(SETTLE_MILLIS)
        check("terminal has focus before probing", terminalHasFocus())
        capture.take("0-start")
        terminalKeysReachClaude()
        appShortcutsFromTerminal(project.id)
        shortcutsOutsideTerminal()
    }

    private suspend fun terminalKeysReachClaude() {
        typeText("alpha beta")
        capture.take("0b-typed")
        println("SCREEN>" + screen().lines().filter { it.isNotBlank() }.joinToString(" / "))
        check("typed text shows in claude input", screen().contains("alpha beta"))
        val before = Snapshot()
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_K)
        check("plain Ctrl+K did not open the palette", !state.paletteOpen)
        check("Ctrl+A then Ctrl+K cleared the claude input (readline kill-line)", !screen().contains("alpha beta"))
        typeText("gamma")
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_U)
        check("Ctrl+U cleared the claude input", !screen().contains("gamma"))
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_J)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_B)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_L)
        check("plain Ctrl+J/B/N/L changed no app state", before.same(Snapshot()) && !state.pickerOpen)
        capture.take("1-after-plain-ctrl")
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_C)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V)
        check("Ctrl+Shift+C/V are not stolen", before.same(Snapshot()))
    }

    private suspend fun appShortcutsFromTerminal(projectId: Long) {
        val chord = { key: Int -> intArrayOf(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, key) }
        press(*chord(KeyEvent.VK_P))
        check("Ctrl+Shift+P opens palette from the terminal", state.paletteOpen)
        capture.take("2-palette")
        press(*chord(KeyEvent.VK_P))
        check("Ctrl+Shift+P closes it again", !state.paletteOpen)
        press(*chord(KeyEvent.VK_P))
        press(KeyEvent.VK_ESCAPE)
        check("Esc closes the palette", !state.paletteOpen)
        press(*chord(KeyEvent.VK_BACK_QUOTE))
        check("Ctrl+Shift+` returns focus to the terminal", terminalHasFocus())
        val sidebar = state.settings.sidebarCollapsed
        press(*chord(KeyEvent.VK_B))
        check("Ctrl+Shift+B toggles the sidebar", state.settings.sidebarCollapsed != sidebar)
        press(*chord(KeyEvent.VK_B))
        val agents = state.settings.agentsOpen
        press(*chord(KeyEvent.VK_J))
        check("Ctrl+Shift+J toggles the agents rail", state.settings.agentsOpen != agents)
        press(*chord(KeyEvent.VK_J))
        val quick = state.settings.quickBarOpen
        press(*chord(KeyEvent.VK_Q))
        check("Ctrl+Shift+Q toggles quick actions", state.settings.quickBarOpen != quick)
        press(*chord(KeyEvent.VK_Q))
        val theme = state.settings.themeMode
        press(*chord(KeyEvent.VK_L))
        check("Ctrl+Shift+L toggles the theme", state.settings.themeMode != theme)
        capture.take("3-theme-toggled")
        press(*chord(KeyEvent.VK_L))
        press(*chord(KeyEvent.VK_T))
        delay(BOOT_MILLIS / 2)
        check("Ctrl+Shift+T starts a second session", state.viewOf(projectId).openSessions.size == 2)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_TAB)
        check("Ctrl+Tab selects the next tab", state.viewOf(projectId).selected == ProjectTab.Git)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_TAB)
        check("Ctrl+Shift+Tab goes back", state.viewOf(projectId).selected is ProjectTab.Session)
        press(*chord(KeyEvent.VK_D))
        check("Ctrl+Shift+D opens Git", state.viewOf(projectId).selected == ProjectTab.Git)
        press(*chord(KeyEvent.VK_A))
        check("Ctrl+Shift+A opens Activity", state.viewOf(projectId).selected == ProjectTab.Activity)
        state.select(projectId, ProjectTab.Session(firstSession))
        delay(SETTLE_MILLIS)
        press(*chord(KeyEvent.VK_M))
        check("Ctrl+Shift+M opens notifications", state.notificationsOpen)
        press(KeyEvent.VK_ESCAPE)
        check("Esc closes notifications while the terminal had focus", !state.notificationsOpen)
        press(KeyEvent.VK_F1)
        check("F1 opens the shortcut sheet", state.shortcutsOpen)
        capture.take("4-shortcut-sheet")
        press(KeyEvent.VK_ESCAPE)
        check("Esc closes the shortcut sheet", !state.shortcutsOpen)
        press(*chord(KeyEvent.VK_W))
        check("Ctrl+Shift+W asks to close the session", state.confirm != null)
        capture.take("5-close-confirm")
        state.confirm = null
        press(KeyEvent.VK_ALT, KeyEvent.VK_0)
        check("Alt+0 shows the overview", state.route == Route.Overview)
        press(KeyEvent.VK_ALT, KeyEvent.VK_1)
        check("Alt+1 opens the first project", state.route is Route.Project)
        capture.take("6-final")
    }

    private suspend fun shortcutsOutsideTerminal() {
        state.navigate(Route.Overview)
        delay(SETTLE_MILLIS)
        robot.mouseMove(capture.window.x + capture.window.width / 2, capture.window.y + capture.window.height / 2)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        delay(SETTLE_MILLIS)
        check("focus left the terminal", !terminalHasFocus())
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_K)
        check("plain Ctrl+K opens the palette outside the terminal", state.paletteOpen)
        press(KeyEvent.VK_ESCAPE)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N)
        check("plain Ctrl+N opens add project outside the terminal", state.pickerOpen)
        state.pickerOpen = false
    }

    private fun screen(): String = TerminalHost.sessionOf(firstSession)?.screenText().orEmpty()

    private suspend fun typeText(text: String) {
        state.send(firstSession, text)
        delay(SETTLE_MILLIS * 2)
    }

    private suspend fun ensureActive() {
        repeat(MAX_ACTIVATION_TRIES) {
            if (clicked && KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow === capture.window) return
            clicked = true
            capture.window.toFront()
            withContext(Dispatchers.IO) {
                robot.mouseMove(capture.window.x + capture.window.width * 3 / 5, capture.window.y + capture.window.height / 2)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            }
            delay(SETTLE_MILLIS)
        }
        error("probe window is not active; refusing to send keys to another application")
    }

    private suspend fun press(vararg keys: Int) {
        ensureActive()
        withContext(Dispatchers.IO) {
            keys.forEach { robot.keyPress(it); Thread.sleep(KEY_DELAY_MILLIS) }
            keys.reversed().forEach { robot.keyRelease(it); Thread.sleep(KEY_DELAY_MILLIS) }
        }
        delay(SETTLE_MILLIS)
    }

    private fun check(name: String, ok: Boolean) {
        results += "${if (ok) "PASS" else "FAIL"}  $name"
        println(results.last() + "   unhandled-presses=" + seen.joinToString(","))
        seen.clear()
    }

    private inner class Snapshot {
        private val settings = state.settings
        private val route = state.route
        fun same(other: Snapshot) = settings == other.settings && route == other.route && !state.paletteOpen && !state.notificationsOpen
    }
}
