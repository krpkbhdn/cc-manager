package com.github.krpkbhdn.ccmanager.ui.web.input

import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.shortcuts.KeyChord
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private data class Mouse(val id: Int, val x: Int, val y: Int, val button: Int, val clicks: Int, val modifiers: Int)

private class RecordingSink : WebInputSink {
    val mouse = mutableListOf<Mouse>()
    val scrolls = mutableListOf<Triple<Int, Int, Int>>()

    override fun mouse(awtId: Int, xDip: Int, yDip: Int, button: Int, clickCount: Int, modifiers: Int) {
        mouse += Mouse(awtId, xDip, yDip, button, clickCount, modifiers)
    }

    override fun scrollBy(xDip: Int, yDip: Int, pixels: Int) {
        scrolls += Triple(xDip, yDip, pixels)
    }
}

class WebInputMapperTest {
    private val sink = RecordingSink()
    private val mapper = WebInputMapper(sink)

    @Test
    fun `click gives press and release with click count one`() {
        mapper.press(10, 20, MouseEvent.BUTTON1, 0, 1000)
        mapper.release(10, 20, MouseEvent.BUTTON1, 0)

        assertThat(sink.mouse).containsExactly(
            Mouse(MouseEvent.MOUSE_PRESSED, 10, 20, MouseEvent.BUTTON1, 1, InputEvent.BUTTON1_DOWN_MASK),
            Mouse(MouseEvent.MOUSE_RELEASED, 10, 20, MouseEvent.BUTTON1, 1, InputEvent.BUTTON1_DOWN_MASK),
        )
    }

    @Test
    fun `second press inside interval and slop is a double click`() {
        mapper.press(10, 20, MouseEvent.BUTTON1, 0, 1000)
        mapper.release(10, 20, MouseEvent.BUTTON1, 0)
        mapper.press(12, 21, MouseEvent.BUTTON1, 0, 1200)

        assertThat(sink.mouse.last().clicks).isEqualTo(2)
    }

    @Test
    fun `slow, distant or other button presses restart the count`() {
        mapper.press(10, 20, MouseEvent.BUTTON1, 0, 1000)
        mapper.press(10, 20, MouseEvent.BUTTON1, 0, 1000 + WebInputMapper.DEFAULT_DOUBLE_CLICK_MILLIS + 1)
        mapper.press(100, 20, MouseEvent.BUTTON1, 0, 1600)
        mapper.press(100, 20, MouseEvent.BUTTON3, 0, 1650)

        assertThat(sink.mouse.map { it.clicks }).containsExactly(1, 1, 1, 1)
    }

    @Test
    fun `third quick press counts three`() {
        repeat(3) { mapper.press(5, 5, MouseEvent.BUTTON1, 0, 1000L + it * 100) }

        assertThat(sink.mouse.map { it.clicks }).containsExactly(1, 2, 3)
    }

    @Test
    fun `move with a button held is a drag carrying the button mask`() {
        mapper.press(1, 1, MouseEvent.BUTTON1, 0, 0)
        mapper.move(30, 40, 0)
        mapper.release(30, 40, MouseEvent.BUTTON1, 0)
        mapper.move(31, 41, 0)

        assertThat(sink.mouse[1]).isEqualTo(Mouse(MouseEvent.MOUSE_DRAGGED, 30, 40, MouseEvent.BUTTON1, 0, InputEvent.BUTTON1_DOWN_MASK))
        assertThat(sink.mouse[3]).isEqualTo(Mouse(MouseEvent.MOUSE_MOVED, 31, 41, MouseEvent.NOBUTTON, 0, 0))
    }

    @Test
    fun `keyboard modifiers are combined with the button mask`() {
        val keys = keyModifierMask(ctrl = true, shift = true, alt = true, meta = true)
        mapper.press(1, 1, MouseEvent.BUTTON3, keys, 0)

        assertThat(sink.mouse.single().modifiers).isEqualTo(
            InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK or
                InputEvent.META_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK,
        )
    }

    @Test
    fun `wheel notch is one hundred page pixels and zero is ignored`() {
        mapper.wheel(7, 8, 1f)
        mapper.wheel(7, 8, -2.5f)
        mapper.wheel(7, 8, 0f)

        assertThat(sink.scrolls).containsExactly(Triple(7, 8, 100), Triple(7, 8, -250))
    }

    @Test
    fun `exit is reported without button`() {
        mapper.exit(3, 4)

        assertThat(sink.mouse.single()).isEqualTo(Mouse(MouseEvent.MOUSE_EXITED, 3, 4, MouseEvent.NOBUTTON, 0, 0))
    }
}

class ShortcutGuardTest {
    private val source = JPanel()
    private val guard = ShortcutGuard(mac = false)

    private fun key(id: Int, code: Int, modifiers: Int = 0, char: Char = KeyEvent.CHAR_UNDEFINED) =
        KeyEvent(source, id, 0L, modifiers, code, char)

    @Test
    fun `app chord is not forwarded and its typed event is dropped`() {
        val chord: KeyChord = AppShortcut.PALETTE.chords(mac = false).first()
        val mods = (if (chord.ctrl) InputEvent.CTRL_DOWN_MASK else 0) or (if (chord.shift) InputEvent.SHIFT_DOWN_MASK else 0) or
            (if (chord.alt) InputEvent.ALT_DOWN_MASK else 0)

        assertThat(guard.allows(key(KeyEvent.KEY_PRESSED, chord.keyCode, mods))).isFalse()
        assertThat(guard.allows(key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, mods, 'p'))).isFalse()
        assertThat(guard.allows(key(KeyEvent.KEY_RELEASED, chord.keyCode, mods))).isFalse()
    }

    @Test
    fun `plain Ctrl letter, Tab and Escape reach the page`() {
        assertThat(guard.allows(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK))).isTrue()
        assertThat(guard.allows(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_TAB))).isTrue()
        assertThat(guard.allows(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE))).isTrue()
        assertThat(guard.allows(key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 0, 'a'))).isTrue()
    }

    @Test
    fun `tab cycling chords stay with the app`() {
        assertThat(guard.allows(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK))).isFalse()
        assertThat(guard.allows(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))).isFalse()
    }
}
