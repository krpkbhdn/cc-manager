package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal enum class CursorKind(val awtType: Int) {
    DEFAULT(Cursor.DEFAULT_CURSOR), CROSSHAIR(Cursor.CROSSHAIR_CURSOR), MOVE(Cursor.MOVE_CURSOR),
    RESIZE_N(Cursor.N_RESIZE_CURSOR), RESIZE_S(Cursor.S_RESIZE_CURSOR), RESIZE_E(Cursor.E_RESIZE_CURSOR), RESIZE_W(Cursor.W_RESIZE_CURSOR),
    RESIZE_NE(Cursor.NE_RESIZE_CURSOR), RESIZE_SW(Cursor.SW_RESIZE_CURSOR), RESIZE_NW(Cursor.NW_RESIZE_CURSOR), RESIZE_SE(Cursor.SE_RESIZE_CURSOR),
}

/** The cursor that tells what a press at this spot would do; [panDragging] is a pan drag in progress (past the move slop), whatever [plan] says. */
internal fun cursorFor(plan: PressPlan?, selected: Int?, panDragging: Boolean = false): CursorKind = when {
    panDragging -> CursorKind.MOVE
    plan == null -> CursorKind.DEFAULT
    else -> when (plan) {
        is PressPlan.Draw -> CursorKind.CROSSHAIR
        is PressPlan.Move -> if (plan.index == selected) CursorKind.MOVE else CursorKind.DEFAULT
        is PressPlan.InsertVertex -> CursorKind.MOVE
        is PressPlan.DragVertex, PressPlan.Deselect, PressPlan.Pan -> CursorKind.DEFAULT
        is PressPlan.Resize -> resizeKind(plan.handle)
    }
}

private fun resizeKind(handle: Handle): CursorKind = when (handle) {
    Handle.NORTH -> CursorKind.RESIZE_N
    Handle.SOUTH -> CursorKind.RESIZE_S
    Handle.EAST -> CursorKind.RESIZE_E
    Handle.WEST -> CursorKind.RESIZE_W
    Handle.NORTH_EAST -> CursorKind.RESIZE_NE
    Handle.SOUTH_WEST -> CursorKind.RESIZE_SW
    Handle.NORTH_WEST -> CursorKind.RESIZE_NW
    Handle.SOUTH_EAST -> CursorKind.RESIZE_SE
    Handle.BODY, Handle.VERTEX -> CursorKind.MOVE
}

private val predefined = CursorKind.entries.associateWith { PointerIcon(Cursor.getPredefinedCursor(it.awtType)) }

internal fun pointerIconOf(kind: CursorKind): PointerIcon = when (kind) {
    CursorKind.DEFAULT -> PointerIcon.Default
    else -> predefined.getValue(kind)
}
