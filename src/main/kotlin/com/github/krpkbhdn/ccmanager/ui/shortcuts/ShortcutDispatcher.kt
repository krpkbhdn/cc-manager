package com.github.krpkbhdn.ccmanager.ui.shortcuts

import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent

/**
 * Sees every key event before the focused component, which is what makes shortcuts work while the terminal
 * owns the keyboard focus. Consumes only the presses [perform] accepted, plus the typed/released events that follow.
 */
class ShortcutDispatcher(
    private val window: Window,
    private val mac: Boolean = Platform.isMac,
    private val terminalFocused: () -> Boolean,
    private val perform: (AppShortcut) -> Boolean,
) : KeyEventDispatcher {
    private val handledKeys = mutableMapOf<Int, KeyChord>()
    private var lastPressHandled = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow !== window) {
            handledKeys.clear()
            lastPressHandled = false
            return false
        }
        val swallow = when (event.id) {
            KeyEvent.KEY_PRESSED -> pressHandled(event).also { lastPressHandled = it }
            KeyEvent.KEY_TYPED -> lastPressHandled && (event.isControlDown || event.isAltDown || event.isMetaDown)
            KeyEvent.KEY_RELEASED -> handledKeys.remove(event.keyCode) != null
            else -> false
        }
        if (swallow) event.consume()
        return swallow
    }

    /** Auto-repeat of a handled chord is swallowed, but a repeat with other modifiers (Shift released first) is a new chord. */
    private fun pressHandled(event: KeyEvent): Boolean {
        val chord = KeyChord.of(event)
        if (handledKeys[event.keyCode] == chord) return true
        val shortcut = ShortcutResolver.resolve(chord, mac, terminalFocused()) ?: return false
        if (!perform(shortcut)) return false
        handledKeys[event.keyCode] = chord
        return true
    }

    fun install(): AutoCloseable {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        manager.addKeyEventDispatcher(this)
        return AutoCloseable { manager.removeKeyEventDispatcher(this) }
    }
}
