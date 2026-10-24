package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.ui.shortcuts.Platform
import com.jediterm.core.input.KeyInputEvent
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.emulator.keyboard.KeyEventProcessingResult
import com.jediterm.terminal.emulator.keyboard.KeyEventProcessingSettings
import com.jediterm.terminal.emulator.keyboard.TerminalKeyEventProcessor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

private const val ESC = "\u001b"
private const val PASTE_START = "$ESC[200~"
private const val PASTE_END = "$ESC[201~"

/** Shift+Enter becomes ESC CR, which Claude reads as a newline; Alt is the Meta key. */
private val KEY_SETTINGS = KeyEventProcessingSettings(shiftEnterSendsEscCR = true, scrollToBottomOnTyping = true, altSendsEscape = true)

/** What a key press means to the terminal widget itself instead of the process behind it. */
internal sealed interface TerminalCommand {
    data class Zoom(val steps: Int) : TerminalCommand

    data object ResetZoom : TerminalCommand

    data object Copy : TerminalCommand

    data object Paste : TerminalCommand
}

private const val CTRL = InputEvent.CTRL_DOWN_MASK
private const val SHIFT = InputEvent.SHIFT_DOWN_MASK
private const val ALT = InputEvent.ALT_DOWN_MASK
private const val META = InputEvent.META_DOWN_MASK

/**
 * Widget commands: zoom with +/-/0, copy and paste with Ctrl+Shift+C/V or Ctrl/Shift+Insert (Cmd+C/V on a Mac).
 * Plain Ctrl+letters stay with the process.
 */
internal fun commandFor(event: KeyInputEvent, mac: Boolean = Platform.isMac): TerminalCommand? {
    if (event.type != KeyInputEvent.Type.PRESSED) return null
    val modifiers = event.modifiersEx
    val primary = modifiers and (if (mac) META else CTRL) != 0
    val shift = modifiers and SHIFT != 0
    if (modifiers and (ALT or (if (mac) CTRL else META)) != 0) return null
    val copyPasteChord = if (mac) primary && !shift else primary && shift
    return when (event.keyCode) {
        KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> TerminalCommand.Zoom(1).takeIf { primary }
        KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> TerminalCommand.Zoom(-1).takeIf { primary && !shift }
        KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> TerminalCommand.ResetZoom.takeIf { primary }
        KeyEvent.VK_C -> TerminalCommand.Copy.takeIf { copyPasteChord }
        KeyEvent.VK_V -> TerminalCommand.Paste.takeIf { copyPasteChord }
        KeyEvent.VK_INSERT -> if (primary && !shift) TerminalCommand.Copy else TerminalCommand.Paste.takeIf { shift && !primary }
        else -> null
    }
}

/** The bytes a key event sends to the process, or null when it sends nothing. */
internal fun keyBytes(event: KeyInputEvent, terminal: Terminal): ByteArray? =
    when (val result = TerminalKeyEventProcessor.processKey(event, terminal, KEY_SETTINGS)) {
        is KeyEventProcessingResult.BytesResult -> result.bytes
        is KeyEventProcessingResult.StringResult -> result.string.toByteArray(Charsets.UTF_8)
        KeyEventProcessingResult.Unhandled -> null
    }


/** Pasted text as a terminal receives typed input: newlines become CR, and ESC is dropped inside a bracketed paste so it cannot end it early. */
internal fun pasteBytes(text: String, bracketed: Boolean): ByteArray {
    val normalized = text.replace("\r\n", "\r").replace('\n', '\r')
    val payload = if (bracketed) PASTE_START + normalized.replace(ESC, "") + PASTE_END else normalized
    return payload.toByteArray(Charsets.UTF_8)
}

/** macOS Option+letter types a symbol such as π; claude expects Meta+letter (ESC letter, e.g. Alt+P opens its model picker), so it is built from the key code. */
internal fun optionLetterBytes(event: KeyInputEvent, mac: Boolean = Platform.isMac): ByteArray? {
    val modifiers = event.modifiersEx
    if (!mac || event.type != KeyInputEvent.Type.PRESSED || modifiers and ALT == 0 || modifiers and (CTRL or META) != 0) return null
    if (event.keyCode !in KeyEvent.VK_A..KeyEvent.VK_Z) return null
    val letter = event.keyCode.toChar().let { if (modifiers and SHIFT != 0) it else it.lowercaseChar() }
    return (ESC + letter).toByteArray()
}
