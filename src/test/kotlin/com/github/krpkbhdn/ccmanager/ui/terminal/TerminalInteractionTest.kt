package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent

private const val ESC = "\u001b"
private const val WIDTH = 1000
private const val HEIGHT = 640
private const val CELL_WIDTH = 8
private const val PADDING_X = 12
private const val PADDING_Y = 8

/** Drives the terminal view through Compose's own input pipeline of an offscreen scene: keys, mouse, wheel. */
@OptIn(InternalComposeUiApi::class)
class TerminalInteractionTest {
    private val connector = ScriptedConnector()
    private val session = TerminalSession(connector)
    private val scene = TerminalScene(session, connector, WIDTH, HEIGHT)
    private val cellHeight = scene.cellHeight
    private var clock = 0L

    @AfterEach
    fun close() {
        scene.close()
        session.close()
    }

    private fun key(type: KeyEventType, code: Int, char: Char, modifiers: Int) = scene.sendKey(
        KeyEvent(
            key = Key(code), type = type, codePoint = char.code.takeIf { char != AwtKeyEvent.CHAR_UNDEFINED } ?: 0,
            isCtrlPressed = modifiers and InputEvent.CTRL_DOWN_MASK != 0, isAltPressed = modifiers and InputEvent.ALT_DOWN_MASK != 0,
            isShiftPressed = modifiers and InputEvent.SHIFT_DOWN_MASK != 0,
        ),
    )

    private fun typed(char: Char, modifiers: Int = 0) = key(KeyEventType.Unknown, AwtKeyEvent.VK_UNDEFINED, char, modifiers)

    private fun pressed(code: Int, char: Char = AwtKeyEvent.CHAR_UNDEFINED, modifiers: Int = 0) = key(KeyEventType.KeyDown, code, char, modifiers)

    private fun point(column: Int, row: Int) = Offset((PADDING_X + column * CELL_WIDTH + 2).toFloat(), (PADDING_Y + row * cellHeight + 2).toFloat())

    private fun pointer(type: PointerEventType, at: Offset, button: PointerButton? = null, scroll: Offset = Offset.Zero) {
        clock += 30
        scene.sendPointer(type, at, clock, button, scroll)
    }

    private fun click(column: Int, row: Int) {
        pointer(PointerEventType.Press, point(column, row), PointerButton.Primary)
        pointer(PointerEventType.Release, point(column, row), PointerButton.Primary)
    }

    private fun written() = scene.session.let { connector.written.joinToString("") { String(it, Charsets.ISO_8859_1) } }

    @Test
    fun typedCyrillicLettersReachTheProcessAsUtf8() {
        typed('ї')
        typed('ж')
        settle(connector, session)
        assertThat(connector.written.flatMap { it.toList() }).containsExactly(0xD1.toByte(), 0x97.toByte(), 0xD0.toByte(), 0xB6.toByte())
    }

    @Test
    fun controlLetterAndArrowAndShiftEnterReachTheProcess() {
        pressed(AwtKeyEvent.VK_C, '\u0003', InputEvent.CTRL_DOWN_MASK)
        pressed(AwtKeyEvent.VK_UP)
        pressed(AwtKeyEvent.VK_ENTER, '\n', InputEvent.SHIFT_DOWN_MASK)
        settle(connector, session)
        assertThat(written()).isEqualTo("\u0003$ESC[A$ESC\r")
    }

    @Test
    fun zoomShortcutResizesTheGridWithoutReachingTheProcess() {
        val before = session.columns
        pressed(AwtKeyEvent.VK_EQUALS, '=', InputEvent.CTRL_DOWN_MASK)
        scene.frame()
        Thread.sleep(300)
        scene.image()
        assertThat(session.cellDp).isEqualTo(DEFAULT_CELL_DP + 1)
        assertThat(session.columns).isLessThan(before)
        assertThat(written()).isEmpty()
        pressed(AwtKeyEvent.VK_0, '0', InputEvent.CTRL_DOWN_MASK)
        assertThat(session.cellDp).isEqualTo(DEFAULT_CELL_DP)
    }

    @Test
    fun draggingSelectsTextAndDoubleClickSelectsAWord() {
        scene.play("${ESC}[2J${ESC}[Hhello brave new world")
        pointer(PointerEventType.Press, point(0, 0), PointerButton.Primary)
        pointer(PointerEventType.Move, point(4, 0), PointerButton.Primary)
        pointer(PointerEventType.Release, point(4, 0), PointerButton.Primary)
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("hello")
        click(8, 0)
        click(8, 0)
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("brave")
        click(8, 0)
        click(8, 0)
        click(8, 0)
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo("hello brave new world")
    }

    @Test
    fun aPlainClickClearsTheSelection() {
        scene.play("${ESC}[2J${ESC}[Hhello")
        session.select(CellSelection(Cell(0, 0), Cell(3, 0)))
        click(10, 3)
        assertThat(session.selection).isNull()
    }

    @Test
    fun wheelScrollsTheScrollbackAndTypingJumpsBack() {
        scene.play((1..80).joinToString("\r\n") { "row $it" })
        pointer(PointerEventType.Scroll, point(5, 5), scroll = Offset(0f, -2f))
        assertThat(session.scrollTarget).isEqualTo(6f)
        typed('x')
        assertThat(session.scrollTarget).isZero()
    }

    @Test
    fun applicationMouseModeReceivesClicksInsteadOfSelection() {
        scene.play("${ESC}[?1000h${ESC}[?1006h")
        click(4, 2)
        settle(connector, session)
        assertThat(written()).isEqualTo("$ESC[<0;5;3M$ESC[<0;5;3m")
        assertThat(session.selection).isNull()
        pointer(PointerEventType.Scroll, point(4, 2), scroll = Offset(0f, 1f))
        assertThat(written()).endsWith("$ESC[<65;5;3M")
    }

    @Test
    fun terminalTakesFocusWhenShown() {
        scene.image()
        assertThat(session.focused).isTrue()
    }
}
