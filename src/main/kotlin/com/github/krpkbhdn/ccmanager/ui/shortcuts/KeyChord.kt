package com.github.krpkbhdn.ccmanager.ui.shortcuts

import java.awt.event.KeyEvent

/** A key plus exactly the modifiers held with it; AWT key codes, so it can be built from a raw [KeyEvent] and unit tested. */
data class KeyChord(val keyCode: Int, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false, val meta: Boolean = false) {

    /** Plain Ctrl+letter is what readline and the Claude TUI use, so it must reach a focused terminal. */
    val clashesWithTerminalApps: Boolean get() = ctrl && !shift && !alt && !meta && keyCode in KeyEvent.VK_A..KeyEvent.VK_Z

    /** Keycap labels in display order, e.g. `Ctrl`, `Shift`, `P`. */
    fun keys(): List<String> = listOfNotNull(
        "Ctrl".takeIf { ctrl },
        "Alt".takeIf { alt },
        "Cmd".takeIf { meta },
        "Shift".takeIf { shift },
        keyName(keyCode),
    )

    companion object {
        fun of(event: KeyEvent) = KeyChord(event.keyCode, event.isControlDown, event.isShiftDown, event.isAltDown, event.isMetaDown)

        private fun keyName(keyCode: Int): String = when (keyCode) {
            KeyEvent.VK_TAB -> "Tab"
            KeyEvent.VK_ESCAPE -> "Esc"
            KeyEvent.VK_BACK_QUOTE -> "`"
            KeyEvent.VK_SLASH -> "/"
            KeyEvent.VK_COMMA -> ","
            else -> KeyEvent.getKeyText(keyCode)
        }
    }
}

internal fun ctrl(keyCode: Int) = KeyChord(keyCode, ctrl = true)

internal fun ctrlShift(keyCode: Int) = KeyChord(keyCode, ctrl = true, shift = true)


internal fun alt(keyCode: Int) = KeyChord(keyCode, alt = true)

internal fun cmd(keyCode: Int) = KeyChord(keyCode, meta = true)

internal fun cmdShift(keyCode: Int) = KeyChord(keyCode, meta = true, shift = true)

internal fun plain(keyCode: Int) = KeyChord(keyCode)
