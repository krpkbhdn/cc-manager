package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalMode
import com.jediterm.terminal.TtyConnector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

private const val ESC = "\u001b"

private class RecordingConnector : TtyConnector {
    val written = CopyOnWriteArrayList<String>()
    private val closed = CountDownLatch(1)

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        closed.await()
        return -1
    }

    override fun write(bytes: ByteArray) {
        written += String(bytes, Charsets.ISO_8859_1)
    }

    override fun write(string: String) {
        written += string
    }

    override fun isConnected() = true

    override fun resize(termSize: TermSize) = Unit

    override fun waitFor(): Int {
        closed.await()
        return 0
    }

    override fun ready() = false

    override fun getName() = "recording"

    override fun close() = closed.countDown()
}

/** What the embedded terminal writes to the process for the keys Claude Code relies on. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TerminalKeyBytesTest {
    private val connector = RecordingConnector()
    private val settings = TerminalSettings(dark = true)
    private lateinit var widget: CcTerminalWidget

    @BeforeAll
    fun openWidget() {
        widget = CcTerminalWidget(80, 24, settings)
        widget.ttyConnector = connector
        widget.start()
        waitForTerminalStarter()
        Thread.sleep(STARTER_WARMUP_MILLIS)
    }

    @AfterAll
    fun closeWidget() {
        connector.close()
        widget.close()
    }

    @Test
    fun shiftEnterSendsEscapeCarriageReturnSoClaudeInsertsANewline() {
        assertThat(press(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK, '\n')).isEqualTo("$ESC\r")
    }

    @Test
    fun altEnterSendsEscapeCarriageReturn() {
        assertThat(press(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK, '\n')).isEqualTo("$ESC\r")
    }

    @Test
    fun plainEnterSendsCarriageReturn() {
        assertThat(press(KeyEvent.VK_ENTER, 0, '\n')).isEqualTo("\r")
    }

    @Test
    fun shiftTabSendsBackTab() {
        assertThat(press(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, '\t')).isEqualTo("$ESC[Z")
    }

    @Test
    fun ctrlArrowsSendModifiedCursorSequences() {
        assertThat(press(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK, KeyEvent.CHAR_UNDEFINED)).isEqualTo("$ESC[1;5C")
        assertThat(press(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK, KeyEvent.CHAR_UNDEFINED)).isEqualTo("$ESC[1;5D")
    }

    @Test
    fun homeEndAndPageKeysAreEscapeSequences() {
        assertThat(press(KeyEvent.VK_HOME, 0, KeyEvent.CHAR_UNDEFINED)).startsWith(ESC)
        assertThat(press(KeyEvent.VK_END, 0, KeyEvent.CHAR_UNDEFINED)).startsWith(ESC)
        assertThat(press(KeyEvent.VK_PAGE_UP, 0, KeyEvent.CHAR_UNDEFINED)).isEqualTo("$ESC[5~")
        assertThat(press(KeyEvent.VK_PAGE_DOWN, 0, KeyEvent.CHAR_UNDEFINED)).isEqualTo("$ESC[6~")
    }

    @Test
    fun ctrlCReachesTheProcessWhenNothingIsSelected() {
        assertThat(press(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, '\u0003')).isEqualTo("\u0003")
    }

    @Test
    fun escapeKeySendsEscapeByte() {
        assertThat(press(KeyEvent.VK_ESCAPE, 0, '\u001b')).isEqualTo(ESC)
    }

    @Test
    fun ctrlPlusAndCtrlMinusZoomWithoutReachingTheProcess() {
        val initial = settings.fontSize
        press(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK, '=')
        assertThat(settings.fontSize).isGreaterThan(initial)
        press(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK, '-')
        press(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, '0')
        assertThat(settings.fontSize).isEqualTo(DEFAULT_FONT_SIZE)
        assertThat(connector.written).isEmpty()
    }

    @Test
    fun bracketedPasteWrapsClipboardTextOnlyWhenTheAppEnabledIt() {
        val clipboard = runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
        org.junit.jupiter.api.Assumptions.assumeTrue(clipboard != null && !java.awt.GraphicsEnvironment.isHeadless())
        clipboard!!.setContents(java.awt.datatransfer.StringSelection("pasted"), null)
        val shiftCtrl = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK

        assertThat(press(KeyEvent.VK_V, shiftCtrl, '\u0016')).isEqualTo("pasted")

        widget.terminal.setModeEnabled(TerminalMode.BracketedPasteMode, true)
        assertThat(press(KeyEvent.VK_V, shiftCtrl, '\u0016')).isEqualTo("$ESC[200~pasted$ESC[201~")
        widget.terminal.setModeEnabled(TerminalMode.BracketedPasteMode, false)
    }

    private fun press(keyCode: Int, modifiers: Int, keyChar: Char): String {
        connector.written.clear()
        val panel = widget.terminalPanel
        val event = KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, keyCode, keyChar)
        panel.processKeyEvent(event)
        val deadline = System.currentTimeMillis() + SETTLE_MILLIS
        while (connector.written.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(30)
        return connector.written.joinToString("")
    }

    private fun waitForTerminalStarter() {
        val deadline = System.currentTimeMillis() + STARTER_TIMEOUT_MILLIS
        while (widget.terminalStarter == null && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    private companion object {
        const val SETTLE_MILLIS = 500L
        const val STARTER_WARMUP_MILLIS = 500L
        const val STARTER_TIMEOUT_MILLIS = 3000L
    }
}
