package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class DictationCaretTest {
    private val cursor = Rect(10f, 20f, 12f, 40f)

    private fun bar(level: Float) = caretBar(cursor, level, 9f, 2f, 8f)

    @Test
    fun `silence is a flat underscore slightly below the line and right of the caret`() {
        val bar = bar(0f)

        assertThat(bar.height).isEqualTo(2f)
        assertThat(bar.width).isEqualTo(9f)
        assertThat(bar.bottom).isEqualTo(41.2f)
        assertThat(bar.left).isEqualTo(18f)
    }

    @Test
    fun `full level reaches as far above the line as the bar sits below it`() {
        val bar = bar(1f)

        assertThat(bar.top).isCloseTo(18.8f, within(0.001f))
        assertThat(bar.bottom).isEqualTo(41.2f)
    }

    @Test
    fun `only the height changes, upwards, and steadily with the level`() {
        val bars = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map(::bar)

        assertThat(bars.map { it.bottom }.distinct()).containsExactly(41.2f)
        assertThat(bars.map { it.width }.distinct()).containsExactly(9f)
        assertThat(bars.map { it.height }).isSorted().doesNotHaveDuplicates()
        assertThat(bars.zipWithNext { a, b -> a.top > b.top }).containsOnly(true)
        assertThat(bar(0.5f).height).isCloseTo(12.2f, within(0.001f))
    }

    @Test
    fun `levels outside zero to one are clamped`() {
        assertThat(bar(3f).top).isEqualTo(bar(1f).top)
        assertThat(bar(-1f).height).isEqualTo(2f)
    }

    @Test
    fun `only a level above the noise floor counts as speech`() {
        assertThat(speechTarget(0.02f)).isZero()
        assertThat(speechTarget(0.5f)).isEqualTo(1f)
    }

    @Test
    fun `the colour is grey in silence and one cycling colour when speaking`() {
        val grey = Color(0xFF888888)

        assertThat(caretColor(grey, 0.3f, 0f)).isEqualTo(grey)
        assertThat(caretColor(grey, 0f, 1f)).isNotEqualTo(caretColor(grey, 0.5f, 1f))
        assertThat(caretColor(grey, 1f, 1f)).isEqualTo(caretColor(grey, 0f, 1f))
        assertThat(caretColor(grey, 0f, 0.5f)).isNotIn(grey, caretColor(grey, 0f, 1f))
    }
}
