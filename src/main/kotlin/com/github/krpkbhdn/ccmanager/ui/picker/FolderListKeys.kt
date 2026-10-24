package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

private const val FIRST_PRINTABLE = 32
private val pathStarters = setOf('/', '\\')

/** Keyboard model of the folder list; returns true when the event was used. */
fun FolderPickerState.handleListKey(event: KeyEvent, rows: List<FolderEntry>, scrollTo: (Int) -> Unit, onConfirm: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val typed = event.utf16CodePoint.takeIf { it >= FIRST_PRINTABLE }?.toChar()
    return when {
        event.isAltPressed -> altNavigation(event.key)
        event.isCtrlPressed || event.isMetaPressed -> (event.key == Key.Enter).also { if (it) onConfirm() }
        event.key == Key.DirectionDown -> step(1, rows, scrollTo)
        event.key == Key.DirectionUp -> step(-1, rows, scrollTo)
        event.key == Key.Enter -> true.also { highlighted?.let(::open) ?: onConfirm() }
        event.key == Key.Backspace -> true.also { up() }
        typed in pathStarters -> true.also { startEditingPath() }
        typed != null && !typed.isISOControl() -> typeAheadJump(typed, rows, scrollTo)
        else -> false
    }
}

private fun FolderPickerState.altNavigation(key: Key): Boolean = when (key) {
    Key.DirectionLeft -> true.also { back() }
    Key.DirectionRight -> true.also { forward() }
    Key.DirectionUp -> true.also { up() }
    else -> false
}

private fun FolderPickerState.step(delta: Int, rows: List<FolderEntry>, scrollTo: (Int) -> Unit): Boolean {
    moveHighlight(delta, rows)
    rows.indexOfFirst { it.path == highlighted }.takeIf { it >= 0 }?.let(scrollTo)
    return true
}

private fun FolderPickerState.typeAheadJump(char: Char, rows: List<FolderEntry>, scrollTo: (Int) -> Unit): Boolean {
    typeAhead(char, rows, System.currentTimeMillis())?.let(scrollTo)
    return true
}
