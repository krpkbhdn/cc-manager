package com.github.krpkbhdn.ccmanager.ui.review

import java.awt.Cursor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewCursorsTest {
    private val edge = EdgeHit(0, 0.5, 0.2)

    @Test
    fun `a vertex of the selected freeform shows the plain arrow`() {
        assertThat(cursorFor(PressPlan.DragVertex(0, 1), 0)).isEqualTo(CursorKind.DEFAULT)
    }

    @Test
    fun `every resize handle shows its own resize cursor`() {
        val expected = mapOf(
            Handle.NORTH to Cursor.N_RESIZE_CURSOR, Handle.SOUTH to Cursor.S_RESIZE_CURSOR,
            Handle.EAST to Cursor.E_RESIZE_CURSOR, Handle.WEST to Cursor.W_RESIZE_CURSOR,
            Handle.NORTH_EAST to Cursor.NE_RESIZE_CURSOR, Handle.SOUTH_WEST to Cursor.SW_RESIZE_CURSOR,
            Handle.NORTH_WEST to Cursor.NW_RESIZE_CURSOR, Handle.SOUTH_EAST to Cursor.SE_RESIZE_CURSOR,
        )

        expected.forEach { (handle, awt) -> assertThat(cursorFor(PressPlan.Resize(0, handle), 0).awtType).describedAs(handle.name).isEqualTo(awt) }
    }

    @Test
    fun `only a pan drag in progress shows the move cursor`() {
        assertThat(cursorFor(PressPlan.Pan, null)).isEqualTo(CursorKind.DEFAULT)
        assertThat(cursorFor(PressPlan.Pan, null, panDragging = true)).isEqualTo(CursorKind.MOVE)
        assertThat(cursorFor(PressPlan.Move(0), 0, panDragging = true)).isEqualTo(CursorKind.MOVE)
        assertThat(cursorFor(null, null, panDragging = true).awtType).isEqualTo(Cursor.MOVE_CURSOR)
    }

    @Test
    fun `the body of the selected block moves, an unselected one keeps the arrow`() {
        assertThat(cursorFor(PressPlan.Move(2), 2)).isEqualTo(CursorKind.MOVE)
        assertThat(cursorFor(PressPlan.Move(1), 2)).isEqualTo(CursorKind.DEFAULT)
        assertThat(cursorFor(PressPlan.Deselect, null)).isEqualTo(CursorKind.DEFAULT)
        assertThat(cursorFor(null, null)).isEqualTo(CursorKind.DEFAULT)
    }

    @Test
    fun `an armed drawing tool shows the crosshair`() {
        assertThat(cursorFor(PressPlan.Draw(ReviewTool.RECTANGLE), null)).isEqualTo(CursorKind.CROSSHAIR)
        assertThat(cursorFor(PressPlan.InsertVertex(0, edge), 0)).isEqualTo(CursorKind.MOVE)
    }
}
