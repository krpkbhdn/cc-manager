package com.github.krpkbhdn.ccmanager.ui.shortcuts

import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent

/**
 * Sees every key event before the focused component, which is what makes shortcuts work while the Swing terminal
 * owns the keyboard focus. Consumes only the presses [perform] accepted, plus the typed/released events that follow.
 */
class ShortcutDispatcher(
    private val window: Window,
    private val mac: Boolean = Platform.isMac,
    private val terminalFocused: () -> Boolean,
    private val perform: (AppShortcut) -> Boolean,
) : KeyEventDispatcher {
    private val handledKeys = mutableSetOf<Int>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow !== window) {
            handledKeys.clear()
            return false
        }
        val swallow = when (event.id) {
            KeyEvent.KEY_PRESSED -> pressHandled(event)
            KeyEvent.KEY_TYPED -> handledKeys.isNotEmpty() && (event.isControlDown || event.isAltDown || event.isMetaDown)
            KeyEvent.KEY_RELEASED -> handledKeys.remove(event.keyCode)
            else -> false
        }
        if (swallow) event.consume()
        return swallow
    }

    private fun pressHandled(event: KeyEvent): Boolean {
        if (event.keyCode in handledKeys) return true
        val shortcut = ShortcutResolver.resolve(KeyChord.of(event), mac, terminalFocused()) ?: return false
        if (!perform(shortcut)) return false
        handledKeys += event.keyCode
        return true
    }

    fun install(): AutoCloseable {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        manager.addKeyEventDispatcher(this)
        return AutoCloseable { manager.removeKeyEventDispatcher(this) }
    }
}
