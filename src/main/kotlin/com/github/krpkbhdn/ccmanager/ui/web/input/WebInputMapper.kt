package com.github.krpkbhdn.ccmanager.ui.web.input

import com.github.krpkbhdn.ccmanager.ui.shortcuts.KeyChord
import com.github.krpkbhdn.ccmanager.ui.shortcuts.Platform
import com.github.krpkbhdn.ccmanager.ui.shortcuts.ShortcutResolver
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/** Receives page-level mouse input; coordinates are DIPs. */
interface WebInputSink {
    fun mouse(awtId: Int, xDip: Int, yDip: Int, button: Int, clickCount: Int, modifiers: Int)

    fun scrollBy(xDip: Int, yDip: Int, pixels: Int)
}

/** The AWT modifier mask of the held keyboard modifiers. */
fun keyModifierMask(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Int =
    (if (ctrl) InputEvent.CTRL_DOWN_MASK else 0) or
        (if (shift) InputEvent.SHIFT_DOWN_MASK else 0) or
        (if (alt) InputEvent.ALT_DOWN_MASK else 0) or
        (if (meta) InputEvent.META_DOWN_MASK else 0)

private fun buttonMask(button: Int): Int = when (button) {
    MouseEvent.BUTTON1 -> InputEvent.BUTTON1_DOWN_MASK
    MouseEvent.BUTTON2 -> InputEvent.BUTTON2_DOWN_MASK
    MouseEvent.BUTTON3 -> InputEvent.BUTTON3_DOWN_MASK
    else -> 0
}

/**
 * Turns pointer gestures into AWT mouse events: tracks the held buttons (drag instead of move),
 * counts clicks within [doubleClickMillis] and [clickSlopDip], and converts wheel notches to page pixels.
 */
class WebInputMapper(
    private val sink: WebInputSink,
    private val doubleClickMillis: Long = DEFAULT_DOUBLE_CLICK_MILLIS,
    private val clickSlopDip: Int = DEFAULT_CLICK_SLOP_DIP,
) {
    private val held = linkedSetOf<Int>()
    private var lastButton = MouseEvent.NOBUTTON
    private var lastPressMillis = Long.MIN_VALUE
    private var lastPressX = 0
    private var lastPressY = 0
    private var clickCount = 0

    fun press(xDip: Int, yDip: Int, button: Int, keys: Int, timeMillis: Long) {
        val repeated = button == lastButton && timeMillis - lastPressMillis <= doubleClickMillis &&
            abs(xDip - lastPressX) <= clickSlopDip && abs(yDip - lastPressY) <= clickSlopDip
        clickCount = if (repeated) clickCount + 1 else 1
        lastButton = button
        lastPressMillis = timeMillis
        lastPressX = xDip
        lastPressY = yDip
        held += button
        sink.mouse(MouseEvent.MOUSE_PRESSED, xDip, yDip, button, clickCount, keys or heldMask())
    }

    fun release(xDip: Int, yDip: Int, button: Int, keys: Int) {
        val modifiers = keys or heldMask()
        held -= button
        sink.mouse(MouseEvent.MOUSE_RELEASED, xDip, yDip, button, if (button == lastButton) clickCount else 1, modifiers)
    }

    fun move(xDip: Int, yDip: Int, keys: Int) {
        val dragging = held.firstOrNull()
        if (dragging == null) sink.mouse(MouseEvent.MOUSE_MOVED, xDip, yDip, MouseEvent.NOBUTTON, 0, keys)
        else sink.mouse(MouseEvent.MOUSE_DRAGGED, xDip, yDip, dragging, 0, keys or heldMask())
    }

    fun exit(xDip: Int, yDip: Int) {
        sink.mouse(MouseEvent.MOUSE_EXITED, xDip, yDip, MouseEvent.NOBUTTON, 0, 0)
    }

    /** One Compose notch (1.0, positive = down) scrolls [PIXELS_PER_NOTCH] page pixels. */
    fun wheel(xDip: Int, yDip: Int, notchesDown: Float) {
        val pixels = (notchesDown * PIXELS_PER_NOTCH).roundToInt()
        if (pixels != 0) sink.scrollBy(xDip, yDip, pixels)
    }

    private fun heldMask(): Int = held.fold(0) { mask, button -> mask or buttonMask(button) }

    companion object {
        const val PIXELS_PER_NOTCH = 100
        const val DEFAULT_DOUBLE_CLICK_MILLIS = 500L
        const val DEFAULT_CLICK_SLOP_DIP = 4
    }
}

/**
 * Keeps the chords the app's global shortcut dispatcher owns away from the page. Plain Ctrl+letter is
 * the page's (copy, paste, select all), exactly like a focused terminal; the typed event that follows an owned press is dropped too.
 */
class ShortcutGuard(private val mac: Boolean = Platform.isMac) {
    private var lastPressOwned = false

    fun allows(event: KeyEvent): Boolean = when (event.id) {
        KeyEvent.KEY_PRESSED -> !owns(event).also { lastPressOwned = it }
        KeyEvent.KEY_TYPED -> !(lastPressOwned && (event.isControlDown || event.isAltDown || event.isMetaDown))
        else -> !owns(event)
    }

    /** Plain Esc only closes an app overlay, and the dispatcher consumes it then, so any Esc that arrives here is the page's. */
    private fun owns(event: KeyEvent): Boolean =
        event.keyCode != KeyEvent.VK_ESCAPE && ShortcutResolver.resolve(KeyChord.of(event), mac, terminalFocused = true) != null
}
