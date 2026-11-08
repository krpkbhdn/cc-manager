package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

private const val ESC = "\u001b"

private const val AWAIT_SECONDS = 10L

class TerminalSessionTest {
    private val connector = ScriptedConnector()
    private val session = TerminalSession(connector)

    @AfterEach
    fun close() = session.close()

    private fun play(text: String) {
        connector.feed(text)
        settle(connector, session)
    }

    private fun line(index: Int) = session.lineText(index)

    @Test
    fun outputLandsInTheBuffer() {
        play("hello\r\nworld")
        assertThat(line(0)).startsWith("hello")
        assertThat(line(1)).startsWith("world")
    }

    @Test
    fun wideCharactersTakeTwoCellsAndEmojiAreSurrogatePairs() {
        play("a漢b\r\n😀x")
        assertThat(clustersOf(line(0).trimEnd()).map { it.cells }).containsExactly(1, 2, 1)
        assertThat(clustersOf(line(1).trimEnd()).map { it.cells }).containsExactly(2, 1)
    }

    @Test
    fun sgrColorsReachTheCellPaint() {
        play("$ESC[38;2;215;119;87mclaude$ESC[0m $ESC[38;5;208mx$ESC[1;4mb")
        val runs = session.buffer.withLock { runsOf(session.buffer.getLine(0), session.theme) }
        assertThat(runs.first { it.text.startsWith("claude") }.paint.foreground.toArgbRgb()).isEqualTo(0xD77757)
        assertThat(runs.first { it.text.startsWith("x") }.paint.foreground.toArgbRgb()).isEqualTo(0xFF8700)
        assertThat(runs.first { it.text.startsWith("b") }.paint.let { it.bold && it.underline }).isTrue()
    }

    @Test
    fun applicationModesAreTracked() {
        play("$ESC[?2004h$ESC[?1000h$ESC[?1006h$ESC[?25l")
        assertThat(session.modes.bracketedPaste).isTrue()
        assertThat(session.modes.mouseMode).isEqualTo(MouseMode.MOUSE_REPORTING_NORMAL)
        assertThat(session.modes.mouseFormat).isEqualTo(MouseFormat.MOUSE_FORMAT_SGR)
        assertThat(session.modes.cursorVisible).isFalse()
        play("$ESC[?1049h")
        assertThat(session.modes.alternateScreen).isTrue()
        assertThat(session.scrollbackLines).isZero()
    }

    @Test
    fun pasteIsWrappedOnlyWhileTheApplicationAsksForIt() {
        play("$ESC[?2004h")
        assertThat(pasteBytes("hi", session.modes.bracketedPaste)).isEqualTo("$ESC[200~hi$ESC[201~".toByteArray())
        play("$ESC[?2004l")
        assertThat(pasteBytes("hi", session.modes.bracketedPaste)).isEqualTo("hi".toByteArray())
    }

    @Test
    fun mouseClicksAreReportedInSgrWhenTheApplicationAsks() {
        play("$ESC[?1000h$ESC[?1006h")
        session.reportMouse(com.jediterm.core.input.MouseEvent(com.jediterm.core.input.MouseEvent.Type.PRESSED, 0, 0), 4, 2)
        session.reportMouse(com.jediterm.core.input.MouseEvent(com.jediterm.core.input.MouseEvent.Type.RELEASED, 0, 0), 4, 2)
        settle(connector, session)
        assertThat(connector.writtenText()).isEqualTo("$ESC[<0;5;3M$ESC[<0;5;3m")
    }

    @Test
    fun wheelOnTheAlternateScreenSendsArrowKeys() {
        play("$ESC[?1049h")
        session.reportWheel(com.jediterm.terminal.emulator.mouse.MouseButtonCodes.SCROLLUP, 0, 2, 0, 0)
        settle(connector, session)
        assertThat(connector.writtenText()).isEqualTo("$ESC[B$ESC[B")
    }

    @Test
    fun typedTextAndPasteReachTheProcess() {
        session.send("ls\r")
        settle(connector, session)
        assertThat(connector.writtenText()).isEqualTo("ls\r")
    }

    @Test
    fun theEscapeKeyReachesTheConnectorAsOneLoneByte() {
        session.onKey(com.jediterm.core.input.KeyInputEvent(com.jediterm.core.input.KeyInputEvent.Type.PRESSED, java.awt.event.KeyEvent.VK_ESCAPE, '\u001b', 0))
        settle(connector, session)
        assertThat(connector.written).hasSize(1)
        assertThat(isLoneEscape(connector.written.single())).isTrue()
    }

    @Test
    fun onlyTheUsersEnterKeyCountsAsRecentEnter() {
        session.send("\r")
        settle(connector, session)
        assertThat(session.enterWithin(60_000)).isFalse()
        session.onKey(com.jediterm.core.input.KeyInputEvent(com.jediterm.core.input.KeyInputEvent.Type.PRESSED, java.awt.event.KeyEvent.VK_ENTER, '\r', 0))
        assertThat(session.enterWithin(60_000)).isTrue()
    }

    @Test
    fun anEscapeOrArrowWithNoTypedTextLeavesNoDraft() {
        val pressed = com.jediterm.core.input.KeyInputEvent.Type.PRESSED
        session.onKey(com.jediterm.core.input.KeyInputEvent(pressed, java.awt.event.KeyEvent.VK_ESCAPE, '\u001b', 0))
        session.onKey(com.jediterm.core.input.KeyInputEvent(pressed, java.awt.event.KeyEvent.VK_UP, '￿', 0))
        assertThat(session.draftMayExist).isFalse()
    }

    @Test
    fun aDraftMayExistFromUserTypingUntilEnterOrCtrlCNotProgrammaticSends() {
        val typed = com.jediterm.core.input.KeyInputEvent.Type.TYPED
        val pressed = com.jediterm.core.input.KeyInputEvent.Type.PRESSED
        session.send("draft")
        assertThat(session.draftMayExist).isFalse()
        session.onKey(com.jediterm.core.input.KeyInputEvent(typed, 0, 'a', 0))
        assertThat(session.draftMayExist).isTrue()
        session.onKey(com.jediterm.core.input.KeyInputEvent(pressed, java.awt.event.KeyEvent.VK_ENTER, '\r', 0))
        assertThat(session.draftMayExist).isFalse()
        session.onKey(com.jediterm.core.input.KeyInputEvent(typed, 0, 'a', 0))
        session.onKey(com.jediterm.core.input.KeyInputEvent(pressed, java.awt.event.KeyEvent.VK_ESCAPE, '\u001b', 0))
        assertThat(session.draftMayExist).isTrue()
        session.onKey(com.jediterm.core.input.KeyInputEvent(pressed, java.awt.event.KeyEvent.VK_C, '\u0003', java.awt.event.InputEvent.CTRL_DOWN_MASK))
        assertThat(session.draftMayExist).isFalse()
    }

    @Test
    fun backspacingEveryTypedCharacterLeavesNoDraft() {
        val typed = com.jediterm.core.input.KeyInputEvent.Type.TYPED
        val backspace = com.jediterm.core.input.KeyInputEvent(com.jediterm.core.input.KeyInputEvent.Type.PRESSED, java.awt.event.KeyEvent.VK_BACK_SPACE, '\b', 0)
        session.onKey(com.jediterm.core.input.KeyInputEvent(typed, 0, 'a', 0))
        session.onKey(com.jediterm.core.input.KeyInputEvent(typed, 0, 'b', 0))
        session.onKey(backspace)
        assertThat(session.draftMayExist).isTrue()
        session.onKey(backspace)
        assertThat(session.draftMayExist).isFalse()
    }

    @Test
    fun aPastedDraftIsClearedByEnter() {
        Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless())
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection("pasted"), null)
        assertThat(session.paste()).isTrue()
        assertThat(session.draftMayExist).isTrue()
        session.onKey(com.jediterm.core.input.KeyInputEvent(com.jediterm.core.input.KeyInputEvent.Type.PRESSED, java.awt.event.KeyEvent.VK_ENTER, '\r', 0))
        assertThat(session.draftMayExist).isFalse()
    }

    @Test
    fun aProgrammaticClearAndEnterAfterUserTypingLeavesNoDraft() {
        session.onKey(com.jediterm.core.input.KeyInputEvent(com.jediterm.core.input.KeyInputEvent.Type.TYPED, 0, 'a', 0))
        assertThat(session.draftMayExist).isTrue()
        session.send("\u0003")
        session.send("/model x")
        session.send("\r")
        assertThat(session.draftMayExist).isFalse()
    }

    @Test
    fun resizeReflowsTheBufferAndTellsTheProcess() {
        val reachedProcess = CountDownLatch(1)
        val observed = object : TtyConnector by connector {
            override fun resize(termSize: TermSize) {
                connector.resize(termSize)
                reachedProcess.countDown()
            }
        }
        val resized = TerminalSession(observed)
        try {
            resized.fit(100, 30)
            assertThat(reachedProcess.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
            assertThat(resized.columns).isEqualTo(100)
            assertThat(resized.rows).isEqualTo(30)
            assertThat(connector.resizes.last().columns).isEqualTo(100)
        } finally {
            resized.close()
        }
    }

    @Test
    fun scrollbackKeepsLinesAndTheViewStaysOnTheSameContent() {
        play((1..60).joinToString("\r\n") { "row $it" })
        assertThat(session.scrollbackLines).isGreaterThan(20)
        session.scrollJumpTo(10f)
        play("\r\nmore\r\nmore")
        assertThat(session.scrollTarget).isEqualTo(12f)
        assertThat(line(-session.scrollbackLines)).startsWith("row 1")
    }

    @Test
    fun selectionFollowsTheContentWhenOutputScrolls() {
        play((1..40).joinToString("\r\n") { "row $it" })
        val before = session.rows - 1
        session.select(CellSelection(Cell(0, before), Cell(4, before)))
        val text = session.selection!!.textIn(session.buffer)
        play("\r\nnext")
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo(text)
    }

    @Test
    fun selectionIsClearedWhenScrollbackShrankEvenIfOutputGrewItAgain() {
        play((1..40).joinToString("\r\n") { "row $it" })
        session.select(CellSelection(Cell(0, 2), Cell(4, 2)))
        connector.feed("$ESC[3J")
        settle(connector, session)
        play((1..60).joinToString("\r\n") { "again $it" })
        assertThat(session.selection).isNull()
    }

    @Test
    fun selectedTextJoinsWrappedLinesAndTrimsBlanks() {
        play("one   \r\ntwo")
        session.select(CellSelection(Cell(0, 0), Cell(5, 1)))
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("one\ntwo")
    }

    @Test
    fun endOfOutputAnnouncesTheEndOfTheSession() {
        connector.close()
        session.awaitModel { "[Session ended" in session.screenText() }
        settle(connector, session)
        assertThat(session.screenText()).contains("[Session ended (exit code 0)]")
    }

    private fun Color.toArgbRgb() = toArgb() and 0xFFFFFF
}
