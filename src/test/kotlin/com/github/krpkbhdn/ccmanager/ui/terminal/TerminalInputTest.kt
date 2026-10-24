package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.input.KeyInputEvent
import com.jediterm.terminal.TerminalMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent.ALT_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent

private const val ESC = "\u001b"
private val NO_CHAR = KeyEvent.CHAR_UNDEFINED

class TerminalInputTest {
    private val terminal = StyleState().let { JediTerminal(ScreenModes {}, TerminalTextBuffer(80, 24, it), it) }

    private fun press(code: Int, modifiers: Int = 0, char: Char = NO_CHAR) = KeyInputEvent(KeyInputEvent.Type.PRESSED, code, char, modifiers)

    private fun typed(char: Char, modifiers: Int = 0) = KeyInputEvent(KeyInputEvent.Type.TYPED, KeyEvent.VK_UNDEFINED, char, modifiers)

    private fun command(event: KeyInputEvent) = commandFor(event, mac = false)

    private fun bytes(event: KeyInputEvent): String? = keyBytes(event, terminal)?.toString(Charsets.ISO_8859_1)

    @Test
    fun typedCharactersAreSentAsUtf8IncludingCyrillic() {
        assertThat(bytes(typed('a'))).isEqualTo("a")
        assertThat(keyBytes(typed('ї'), terminal)).containsExactly(0xD1.toByte(), 0x97.toByte())
        assertThat(keyBytes(typed('Ж'), terminal)).containsExactly(0xD0.toByte(), 0x96.toByte())
        assertThat(keyBytes(typed('€'), terminal)).containsExactly(0xE2.toByte(), 0x82.toByte(), 0xAC.toByte())
    }

    @Test
    fun altGraphCharactersArriveAsTypedWithCtrlAndAlt() {
        assertThat(bytes(typed('@', CTRL_DOWN_MASK or ALT_DOWN_MASK))).isEqualTo("@")
    }

    @Test
    fun enterBackspaceTabAndEscape() {
        assertThat(bytes(press(KeyEvent.VK_ENTER))).isEqualTo("\r")
        assertThat(bytes(press(KeyEvent.VK_BACK_SPACE))).isEqualTo("\u007f")
        assertThat(bytes(press(KeyEvent.VK_TAB, char = '\t'))).isEqualTo("\t")
        assertThat(bytes(press(KeyEvent.VK_TAB, SHIFT_DOWN_MASK))).isEqualTo("$ESC[Z")
        assertThat(bytes(press(KeyEvent.VK_ESCAPE, char = '\u001b'))).isEqualTo(ESC)
    }

    @Test
    fun shiftEnterAndAltEnterSendEscapeThenCarriageReturn() {
        assertThat(bytes(press(KeyEvent.VK_ENTER, SHIFT_DOWN_MASK))).isEqualTo("$ESC\r")
        assertThat(bytes(press(KeyEvent.VK_ENTER, ALT_DOWN_MASK))).isEqualTo("$ESC\r")
    }

    @Test
    fun arrowsAndNavigationKeys() {
        assertThat(bytes(press(KeyEvent.VK_UP))).isEqualTo("$ESC[A")
        assertThat(bytes(press(KeyEvent.VK_DOWN))).isEqualTo("$ESC[B")
        assertThat(bytes(press(KeyEvent.VK_RIGHT))).isEqualTo("$ESC[C")
        assertThat(bytes(press(KeyEvent.VK_LEFT))).isEqualTo("$ESC[D")
        assertThat(bytes(press(KeyEvent.VK_HOME))).isEqualTo("$ESC[H")
        assertThat(bytes(press(KeyEvent.VK_END))).isEqualTo("$ESC[F")
        assertThat(bytes(press(KeyEvent.VK_PAGE_UP))).isEqualTo("$ESC[5~")
        assertThat(bytes(press(KeyEvent.VK_PAGE_DOWN))).isEqualTo("$ESC[6~")
        assertThat(bytes(press(KeyEvent.VK_INSERT))).isEqualTo("$ESC[2~")
        assertThat(bytes(press(KeyEvent.VK_DELETE))).isEqualTo("$ESC[3~")
    }

    @Test
    fun modifiedArrowsUseXtermParameters() {
        assertThat(bytes(press(KeyEvent.VK_UP, CTRL_DOWN_MASK))).isEqualTo("$ESC[1;5A")
        assertThat(bytes(press(KeyEvent.VK_LEFT, SHIFT_DOWN_MASK))).isEqualTo("$ESC[1;2D")
        assertThat(bytes(press(KeyEvent.VK_RIGHT, CTRL_DOWN_MASK or SHIFT_DOWN_MASK))).isEqualTo("$ESC[1;6C")
        assertThat(bytes(press(KeyEvent.VK_DELETE, CTRL_DOWN_MASK))).isEqualTo("$ESC[3;5~")
    }

    @Test
    fun applicationCursorKeysSwitchArrowsToSs3() {
        terminal.setModeEnabled(TerminalMode.CursorKey, true)
        assertThat(bytes(press(KeyEvent.VK_UP))).isEqualTo("${ESC}OA")
        assertThat(bytes(press(KeyEvent.VK_LEFT))).isEqualTo("${ESC}OD")
        terminal.setModeEnabled(TerminalMode.CursorKey, false)
        assertThat(bytes(press(KeyEvent.VK_UP))).isEqualTo("$ESC[A")
    }

    @Test
    fun functionKeys() {
        val expected = listOf("${ESC}OP", "${ESC}OQ", "${ESC}OR", "${ESC}OS", "$ESC[15~", "$ESC[17~", "$ESC[18~", "$ESC[19~", "$ESC[20~", "$ESC[21~", "$ESC[23~", "$ESC[24~")
        expected.forEachIndexed { index, sequence -> assertThat(bytes(press(KeyEvent.VK_F1 + index))).`as`("F${index + 1}").isEqualTo(sequence) }
        assertThat(bytes(press(KeyEvent.VK_F5, SHIFT_DOWN_MASK))).isEqualTo("$ESC[15;2~")
    }

    @Test
    fun controlLettersAndPunctuationBecomeControlCodes() {
        for (letter in 'A'..'Z') {
            val code = (letter - '@').toChar()
            assertThat(bytes(press(letter.code, CTRL_DOWN_MASK, code))).`as`("Ctrl+$letter").isEqualTo(code.toString())
        }
        assertThat(bytes(press(KeyEvent.VK_OPEN_BRACKET, CTRL_DOWN_MASK, '\u001b'))).isEqualTo(ESC)
        assertThat(bytes(press(KeyEvent.VK_BACK_SLASH, CTRL_DOWN_MASK, '\u001c'))).isEqualTo("\u001c")
        assertThat(bytes(press(KeyEvent.VK_CLOSE_BRACKET, CTRL_DOWN_MASK, '\u001d'))).isEqualTo("\u001d")
        assertThat(bytes(press(KeyEvent.VK_6, CTRL_DOWN_MASK or SHIFT_DOWN_MASK, '\u001e'))).isEqualTo("\u001e")
        assertThat(bytes(press(KeyEvent.VK_MINUS, CTRL_DOWN_MASK or SHIFT_DOWN_MASK, '\u001f'))).isEqualTo("\u001f")
        assertThat(bytes(press(KeyEvent.VK_SPACE, CTRL_DOWN_MASK, ' '))).isEqualTo("\u0000")
    }

    @Test
    fun altPlusCharacterSendsEscapeThenTheCharacter() {
        assertThat(bytes(press(KeyEvent.VK_B, ALT_DOWN_MASK, 'b'))).isEqualTo("${ESC}b")
    }

    @Test
    fun barePressesOfPrintableAndModifierKeysSendNothing() {
        assertThat(keyBytes(press(KeyEvent.VK_A, char = 'a'), terminal)).isNull()
        assertThat(keyBytes(press(KeyEvent.VK_SHIFT), terminal)).isNull()
    }

    @Test
    fun widgetCommandsDoNotClashWithProcessShortcuts() {
        assertThat(command(press(KeyEvent.VK_EQUALS, CTRL_DOWN_MASK))).isEqualTo(TerminalCommand.Zoom(1))
        assertThat(command(press(KeyEvent.VK_EQUALS, CTRL_DOWN_MASK or SHIFT_DOWN_MASK))).isEqualTo(TerminalCommand.Zoom(1))
        assertThat(command(press(KeyEvent.VK_MINUS, CTRL_DOWN_MASK))).isEqualTo(TerminalCommand.Zoom(-1))
        assertThat(command(press(KeyEvent.VK_MINUS, CTRL_DOWN_MASK or SHIFT_DOWN_MASK))).isNull()
        assertThat(command(press(KeyEvent.VK_0, CTRL_DOWN_MASK))).isEqualTo(TerminalCommand.ResetZoom)
        assertThat(command(press(KeyEvent.VK_C, CTRL_DOWN_MASK or SHIFT_DOWN_MASK))).isEqualTo(TerminalCommand.Copy)
        assertThat(command(press(KeyEvent.VK_C, CTRL_DOWN_MASK))).isNull()
        assertThat(command(press(KeyEvent.VK_V, CTRL_DOWN_MASK or SHIFT_DOWN_MASK))).isEqualTo(TerminalCommand.Paste)
        assertThat(command(press(KeyEvent.VK_V, CTRL_DOWN_MASK))).isNull()
        assertThat(command(press(KeyEvent.VK_INSERT, CTRL_DOWN_MASK))).isEqualTo(TerminalCommand.Copy)
        assertThat(command(press(KeyEvent.VK_INSERT, SHIFT_DOWN_MASK))).isEqualTo(TerminalCommand.Paste)
        assertThat(command(press(KeyEvent.VK_INSERT))).isNull()
        assertThat(command(typed('c', CTRL_DOWN_MASK or SHIFT_DOWN_MASK))).isNull()
    }

    @Test
    fun commandKeyDrivesCopyPasteAndZoomOnAMac() {
        val meta = java.awt.event.InputEvent.META_DOWN_MASK
        assertThat(commandFor(press(KeyEvent.VK_C, meta), mac = true)).isEqualTo(TerminalCommand.Copy)
        assertThat(commandFor(press(KeyEvent.VK_V, meta), mac = true)).isEqualTo(TerminalCommand.Paste)
        assertThat(commandFor(press(KeyEvent.VK_EQUALS, meta), mac = true)).isEqualTo(TerminalCommand.Zoom(1))
        assertThat(commandFor(press(KeyEvent.VK_C, CTRL_DOWN_MASK), mac = true)).isNull()
    }

    @Test
    fun pasteTurnsNewlinesIntoCarriageReturns() {
        assertThat(String(pasteBytes("a\nb\r\nc", bracketed = false))).isEqualTo("a\rb\rc")
    }

    @Test
    fun bracketedPasteWrapsTheTextAndStripsEscapes() {
        assertThat(String(pasteBytes("x${ESC}[201~y\n", bracketed = true))).isEqualTo("$ESC[200~x[201~y\r$ESC[201~")
    }
}
