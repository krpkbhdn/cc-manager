package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.jediterm.core.input.MouseEvent
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val WHEEL_LINES_PER_NOTCH = 3f
private const val AUTOSCROLL_MILLIS = 50L

/**
 * Mouse handling of a terminal view: reports clicks, drags and the wheel to applications that asked for them, and
 * otherwise selects text (drag, double and triple click, Shift+click) and scrolls the scrollback.
 */
internal fun Modifier.terminalPointer(
    session: TerminalSession,
    geometry: State<TerminalGeometry>,
    size: State<IntSize>,
    onContextMenu: (Offset) -> Unit,
): Modifier = pointerInput(session) {
    coroutineScope {
        val gestures = PointerGestures(session, geometry, size, this, onContextMenu)
        awaitPointerEventScope {
            while (true) gestures.onEvent(awaitPointerEvent())
        }
    }
}

/** Turns fractional scroll deltas (touchpads) into whole lines, keeping the remainder for the next event. */
internal class WheelLines {
    private var pending = 0f

    fun take(delta: Float): Int {
        if (delta * pending < 0f) pending = 0f
        pending += delta
        val whole = pending.toInt()
        pending -= whole
        return whole
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class PointerGestures(
    private val session: TerminalSession,
    private val geometry: State<TerminalGeometry>,
    private val size: State<IntSize>,
    private val scope: CoroutineScope,
    private val onContextMenu: (Offset) -> Unit,
) {
    private val clicks = ClickCounter()
    private val wheelLines = WheelLines()
    private var selecting = false
    private var reportingDrag = false
    private var anchor = Cell(0, 0)
    private var lastPoint = Offset.Zero
    private var autoscroll: Job? = null

    fun onEvent(event: PointerEvent) {
        val change = event.changes.firstOrNull() ?: return
        lastPoint = change.position
        when (event.type) {
            PointerEventType.Press -> if (onPress(event, change.position, change.uptimeMillis)) change.consume()
            PointerEventType.Move, PointerEventType.Enter -> if (onMove(event, change.position)) change.consume()
            PointerEventType.Release -> if (onRelease(event, change.position)) change.consume()
            PointerEventType.Scroll -> if (onWheel(event, change.position, change.scrollDelta.y)) change.consume()
        }
    }

    private fun onPress(event: PointerEvent, point: Offset, timeMillis: Long): Boolean {
        runCatching { session.focusRequester.requestFocus() }
        if (event.button == PointerButton.Secondary) {
            onContextMenu(point)
            return true
        }
        if (session.mouseReporting && !event.keyboardModifiers.isShiftPressed) {
            reportingDrag = true
            return report(MouseEvent.Type.PRESSED, event, point)
        }
        if (event.button == PointerButton.Primary) beginSelection(event, point, timeMillis)
        return true
    }

    private fun onMove(event: PointerEvent, point: Offset): Boolean {
        if (reportingDrag) return report(MouseEvent.Type.DRAGGED, event, point)
        if (selecting) {
            extendSelection(point)
            return true
        }
        val anyPressed = event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed
        if (session.mouseReporting && !anyPressed && !event.keyboardModifiers.isShiftPressed) return report(MouseEvent.Type.MOVED, event, point)
        return false
    }

    private fun onRelease(event: PointerEvent, point: Offset): Boolean {
        autoscroll?.cancel()
        selecting = false
        if (!reportingDrag) return false
        reportingDrag = false
        return report(MouseEvent.Type.RELEASED, event, point)
    }

    private fun onWheel(event: PointerEvent, point: Offset, delta: Float): Boolean {
        if (delta == 0f) return false
        val shift = event.keyboardModifiers.isShiftPressed
        if ((session.mouseReporting && !shift) || session.modes.alternateScreen) {
            val (column, row) = geometry.value.gridCellAt(point, session.columns, session.rows)
            val whole = wheelLines.take(delta)
            if (whole == 0) return true
            val code = if (whole > 0) MouseButtonCodes.SCROLLUP else MouseButtonCodes.SCROLLDOWN
            return session.reportWheel(code, modifierFlags(event), abs(whole), column, row)
        }
        session.scrollBy(-delta * WHEEL_LINES_PER_NOTCH)
        return true
    }

    private fun report(type: MouseEvent.Type, event: PointerEvent, point: Offset): Boolean {
        val button = when {
            event.button == PointerButton.Tertiary || event.buttons.isTertiaryPressed -> MouseButtonCodes.MIDDLE
            event.button == PointerButton.Primary || event.buttons.isPrimaryPressed || type == MouseEvent.Type.RELEASED -> MouseButtonCodes.LEFT
            else -> MouseButtonCodes.NONE
        }
        val (column, row) = geometry.value.gridCellAt(point, session.columns, session.rows)
        return session.reportMouse(MouseEvent(type, button, modifierFlags(event)), column, row)
    }

    private fun beginSelection(event: PointerEvent, point: Offset, timeMillis: Long) {
        val cell = cellAt(point)
        val extend = event.keyboardModifiers.isShiftPressed && session.selection != null
        val count = clicks.register(cell, timeMillis)
        selecting = true
        anchor = cell
        when {
            extend -> session.select(session.selection?.extendedTo(cell))
            count > 1 -> session.select(CellSelection.of(cell, count, session.lineText(cell.line), session.columns))
            else -> session.clearSelection()
        }
        autoscroll = scope.launch { autoscrollWhileOutside() }
    }

    private fun extendSelection(point: Offset) {
        val cell = cellAt(point)
        val current = session.selection
        if (current == null && cell == anchor) return
        session.select(CellSelection(current?.anchor ?: anchor, cell))
    }

    private suspend fun autoscrollWhileOutside() {
        while (scope.isActive) {
            delay(AUTOSCROLL_MILLIS)
            val top = geometry.value.paddingY
            val bottom = size.value.height - top
            val direction = when {
                lastPoint.y < top -> 1f
                lastPoint.y > bottom -> -1f
                else -> continue
            }
            session.scrollJumpTo(session.scrollTarget + direction)
            extendSelection(lastPoint)
        }
    }

    private fun cellAt(point: Offset) =
        geometry.value.bufferCellAt(point, session.scrollShown, session.columns, session.rows, session.scrollbackLines)

    private fun modifierFlags(event: PointerEvent): Int {
        val keys = event.keyboardModifiers
        var flags = 0
        if (keys.isShiftPressed) flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG
        if (keys.isMetaPressed || keys.isAltPressed) flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG
        if (keys.isCtrlPressed) flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG
        return flags
    }
}
