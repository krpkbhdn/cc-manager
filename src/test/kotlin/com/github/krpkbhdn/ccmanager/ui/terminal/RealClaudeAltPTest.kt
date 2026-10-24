package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import com.pty4j.PtyProcessBuilder
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

private const val WAIT_MILLIS = 60_000L
private const val POLL_MILLIS = 100L
private const val COLUMNS = 120
private const val ROWS = 32

/**
 * Real claude (no prompt sent) inside the app's terminal widget: Alt+P as real AWT key events through the Compose key
 * pipeline must open claude's own model picker, whose text is then read from the emulator's screen.
 */
class RealClaudeAltPTest {

    @Test
    fun altPOpensClaudesModelPicker() {
        assumeTrue(System.getenv("CCM_REAL_CLAUDE") == "1")
        assumeFalse(GraphicsEnvironment.isHeadless())
        val process = PtyProcessBuilder(arrayOf(CLAUDE_EXE)).setDirectory(SANDBOX_DIR).setEnvironment(cleanDevEnvironment())
            .setUseWinConPty(true).setInitialColumns(COLUMNS).setInitialRows(ROWS).start()
        val session = TerminalSession(PtyTtyConnector(process, "claude") { })
        var frame: JFrame? = null
        try {
            SwingUtilities.invokeAndWait {
                frame = JFrame().apply {
                    val panel = ComposePanel()
                    panel.setContent { TerminalSessionView(session, Modifier.fillMaxSize(), TerminalTheme()) }
                    contentPane.add(panel)
                    setSize(1000, 700)
                    isVisible = true
                }
            }
            await("terminal focused", session) { session.focused && KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner != null }
            await("claude prompt", session) { session.screenText().contains("❯") || session.screenText().contains("for shortcuts") }
            Thread.sleep(2_000)
            val target = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner!!
            SwingUtilities.invokeAndWait { target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, System.nanoTime(), InputEvent.ALT_DOWN_MASK, KeyEvent.VK_P, 'p')) }
            SwingUtilities.invokeAndWait { target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_TYPED, System.nanoTime(), InputEvent.ALT_DOWN_MASK, KeyEvent.VK_UNDEFINED, 'p')) }
            await("model picker", session) { session.screenText().contains("model", ignoreCase = true) && session.screenText().contains("Opus") }
            println("ALT_P_SCREEN=\n" + session.screenText().lines().filter { it.isNotBlank() }.joinToString("\n"))
            assertThat(session.screenText()).containsIgnoringCase("model")
        } finally {
            runCatching { ProcessBuilder("taskkill", "/PID", process.pid().toString(), "/T", "/F").start().waitFor() }
            SwingUtilities.invokeAndWait { frame?.dispose() }
            session.close()
        }
    }

    private fun await(what: String, session: TerminalSession, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertThat(condition()).describedAs("$what within ${WAIT_MILLIS}ms, screen: ${session.screenText().lines().filter { it.isNotBlank() }}").isTrue()
    }
}
