package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import com.jediterm.core.input.KeyInputEvent
import java.awt.event.InputEvent

/**
 * Compose key event as the emulator's key input. Presses carry the AWT key code, characters typed by the keyboard
 * layout (Cyrillic, AltGr, dead keys) arrive as events of type Unknown with the character in the code point.
 */
internal fun KeyEvent.toKeyInput(): KeyInputEvent? {
    val kind = when (type) {
        KeyEventType.KeyDown -> KeyInputEvent.Type.PRESSED
        KeyEventType.Unknown -> KeyInputEvent.Type.TYPED
        else -> return null
    }
    val char = utf16CodePoint.takeIf { it in 1..Char.MAX_VALUE.code }?.toChar() ?: CHAR_UNDEFINED
    if (kind == KeyInputEvent.Type.TYPED && char == CHAR_UNDEFINED) return null
    return KeyInputEvent(kind, key.nativeKeyCode, char, modifiersEx())
}

private const val CHAR_UNDEFINED = '￿'

private fun KeyEvent.modifiersEx(): Int {
    var modifiers = 0
    if (isCtrlPressed) modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
    if (isShiftPressed) modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK
    if (isAltPressed) modifiers = modifiers or InputEvent.ALT_DOWN_MASK
    if (isMetaPressed) modifiers = modifiers or InputEvent.META_DOWN_MASK
    return modifiers
}
