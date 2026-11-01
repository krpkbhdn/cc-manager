package com.github.krpkbhdn.ccmanager.ui.git.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewAnchorTest {
    @Test
    fun `finds the anchor after it moved`() {
        val file = listOf("a", "b", "c", "x", "y", "d")
        assertThat(locate(listOf("x", "y"), file, near = 0)).isEqualTo(3..4)
    }

    @Test
    fun `ignores trailing whitespace`() {
        assertThat(locate(listOf("x  "), listOf("a", "x"), near = 0)).isEqualTo(1..1)
    }

    @Test
    fun `picks the duplicate nearest to the old position`() {
        val file = listOf("x", "a", "a", "a", "a", "x")
        assertThat(locate(listOf("x"), file, near = 4)).isEqualTo(5..5)
        assertThat(locate(listOf("x"), file, near = 1)).isEqualTo(0..0)
    }

    @Test
    fun `returns null when the anchor is gone or empty`() {
        assertThat(locate(listOf("zzz"), listOf("a", "b"), near = 0)).isNull()
        assertThat(locate(emptyList(), listOf("a"), near = 0)).isNull()
    }
}
