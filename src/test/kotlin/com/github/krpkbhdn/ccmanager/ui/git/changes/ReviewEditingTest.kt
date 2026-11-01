package com.github.krpkbhdn.ccmanager.ui.git.changes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewEditingTest {
    private val hunks = listOf(Hunk(0, 0, 1..5), Hunk(1, 6, 7..9))
    private val all: (Int) -> Boolean = { true }

    @Test
    fun `a plain click selects the one row`() {
        assertThat(extendSelection(2..3, 4, false, hunks, all)).isEqualTo(4..4)
    }

    @Test
    fun `shift click grows the range in both directions inside the hunk`() {
        assertThat(extendSelection(3..3, 5, true, hunks, all)).isEqualTo(3..5)
        assertThat(extendSelection(3..3, 1, true, hunks, all)).isEqualTo(1..3)
    }

    @Test
    fun `shift click in another hunk starts a new selection`() {
        assertThat(extendSelection(3..3, 8, true, hunks, all)).isEqualTo(8..8)
    }

    @Test
    fun `hunk headers and unselectable rows keep the selection`() {
        assertThat(extendSelection(3..3, 6, false, hunks, all)).isEqualTo(3..3)
        assertThat(extendSelection(3..3, 4, false, hunks) { it != 4 }).isEqualTo(3..3)
        assertThat(extendSelection(null, 0, true, hunks, all)).isNull()
    }
}
