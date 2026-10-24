package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

private const val ESC = "\u001b"
private const val FOCUS_WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 50L

/**
 * Real AWT events dispatched straight to the component of an in-process Compose panel (no OS-level input), so the
 * conversion done by Compose itself is part of what is tested.
 */
class TerminalAwtKeyPipelineTest {
    private val connector = ScriptedConnector()
    private val session = TerminalSession(connector)
    private var frame: JFrame? = null

    @BeforeEach
    fun open() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        SwingUtilities.invokeAndWait {
            frame = JFrame().apply {
                val panel = ComposePanel()
                panel.setContent { TerminalSessionView(session, Modifier.fillMaxSize(), TerminalTheme()) }
                contentPane.add(panel)
                setSize(640, 400)
                isVisible = true
            }
        }
        val deadline = System.currentTimeMillis() + FOCUS_WAIT_MILLIS
        while ((!session.focused || KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner == null) && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    }

    @AfterEach
    fun close() {
        SwingUtilities.invokeAndWait { frame?.dispose() }
        session.close()
    }

    private fun dispatch(id: Int, code: Int, char: Char, modifiers: Int = 0) {
        val target = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: frame!!
        SwingUtilities.invokeAndWait { target.dispatchEvent(KeyEvent(target, id, System.nanoTime(), modifiers, code, char)) }
    }

    private fun typed(char: Char, modifiers: Int = 0) = dispatch(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, char, modifiers)

    private fun pressed(code: Int, char: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0) = dispatch(KeyEvent.KEY_PRESSED, code, char, modifiers)

    private fun written(): String {
        settle(connector, session)
        return connector.written.joinToString("") { String(it, Charsets.ISO_8859_1) }
    }

    @Test
    fun layoutCharactersAndSpecialKeysBecomeTheRightBytes() {
        assumeFalse(!session.focused)
        typed('ї')
        typed('a')
        pressed(KeyEvent.VK_C, '\u0003', InputEvent.CTRL_DOWN_MASK)
        pressed(KeyEvent.VK_B, 'b', InputEvent.ALT_DOWN_MASK)
        typed('b', InputEvent.ALT_DOWN_MASK)
        pressed(KeyEvent.VK_P, 'p', InputEvent.ALT_DOWN_MASK)
        typed('p', InputEvent.ALT_DOWN_MASK)
        pressed(KeyEvent.VK_ENTER, '\n', InputEvent.SHIFT_DOWN_MASK)
        pressed(KeyEvent.VK_UP)
        pressed(KeyEvent.VK_TAB, '\t', InputEvent.SHIFT_DOWN_MASK)
        typed('@', InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        typed('\uD83D')
        typed('\uDE00')
        val expected = "Ñ\u0097a\u0003${ESC}b${ESC}p$ESC\r$ESC[A$ESC[Z@" + String("😀".toByteArray(), Charsets.ISO_8859_1)
        assertThat(written()).isEqualTo(expected)
    }
}
