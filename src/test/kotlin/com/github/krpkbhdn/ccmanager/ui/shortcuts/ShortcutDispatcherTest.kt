package com.github.krpkbhdn.ccmanager.ui.shortcuts

import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

class ShortcutDispatcherTest {
    private val window = mock(Window::class.java)
    private val manager = mock(KeyboardFocusManager::class.java)
    private val performed = mutableListOf<AppShortcut>()
    private val dispatcher = ShortcutDispatcher(window, mac = false, terminalFocused = { true }) { performed += it; true }
    private lateinit var focus: MockedStatic<KeyboardFocusManager>

    @BeforeEach
    fun focusWindow() {
        focus = mockStatic(KeyboardFocusManager::class.java)
        focus.`when`<KeyboardFocusManager> { KeyboardFocusManager.getCurrentKeyboardFocusManager() }.thenReturn(manager)
        `when`(manager.activeWindow).thenReturn(window)
    }

    @AfterEach
    fun release() {
        focus.close()
    }

    private fun key(id: Int, keyCode: Int, modifiers: Int = 0, char: Char = KeyEvent.CHAR_UNDEFINED) =
        KeyEvent(window, id, 0L, modifiers, keyCode, char)

    private val ctrlShift = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK

    @Test
    fun `a handled press swallows its typed and released events`() {
        val press = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_P, ctrlShift)

        assertThat(dispatcher.dispatchKeyEvent(press)).isTrue()
        assertThat(press.isConsumed).isTrue()
        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, InputEvent.CTRL_DOWN_MASK, 'P'))).isTrue()
        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_P))).isTrue()
        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_P))).isFalse()
        assertThat(performed).containsExactly(AppShortcut.PALETTE)
    }

    @Test
    fun `auto-repeat of the handled chord is swallowed without running the shortcut again`() {
        dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_P, ctrlShift))

        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_P, ctrlShift))).isTrue()
        assertThat(performed).hasSize(1)
    }

    @Test
    fun `plain Ctrl+P repeating after Shift was released reaches the terminal`() {
        dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_P, ctrlShift))

        val repeat = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)

        assertThat(dispatcher.dispatchKeyEvent(repeat)).isFalse()
        assertThat(repeat.isConsumed).isFalse()
        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, InputEvent.CTRL_DOWN_MASK, '\u0010'))).isFalse()
    }

    @Test
    fun `an inactive window forgets handled keys`() {
        dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_P, ctrlShift))
        `when`(manager.activeWindow).thenReturn(null)

        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_P))).isFalse()

        `when`(manager.activeWindow).thenReturn(window)
        assertThat(dispatcher.dispatchKeyEvent(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_P))).isFalse()
    }
}
