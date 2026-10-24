package com.github.krpkbhdn.ccmanager.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WindowRestoreTest {
    private val left = ScreenArea(0, 0, 1920, 1040)
    private val right = ScreenArea(1920, 0, 1280, 1000)

    @Test
    fun `bounds fully on a screen stay untouched`() {
        val bounds = WindowBounds(1280, 820, 100, 80)
        assertThat(fitToScreens(bounds, listOf(left))).isEqualTo(bounds)
    }

    @Test
    fun `a window on a disconnected monitor loses its position`() {
        val restored = fitToScreens(WindowBounds(1280, 820, 2500, 100), listOf(left))
        assertThat(restored.x).isNull()
        assertThat(restored.y).isNull()
        assertThat(restored.width).isEqualTo(1280)
    }

    @Test
    fun `a window mostly off screen is pulled back inside`() {
        val restored = fitToScreens(WindowBounds(1000, 700, 1500, 600), listOf(left))
        assertThat(restored).isEqualTo(WindowBounds(1000, 700, 920, 340))
    }

    @Test
    fun `a window on the second monitor stays there`() {
        val bounds = WindowBounds(1000, 700, 2000, 50)
        assertThat(fitToScreens(bounds, listOf(left, right))).isEqualTo(bounds)
    }

    @Test
    fun `oversized windows shrink to the screen`() {
        val restored = fitToScreens(WindowBounds(5000, 3000, 0, 0), listOf(left))
        assertThat(restored).isEqualTo(WindowBounds(1920, 1040, 0, 0))
    }

    @Test
    fun `unplaced windows only get their size clamped and keep the maximized flag`() {
        val restored = fitToScreens(WindowBounds(4000, 600, maximized = true), listOf(left))
        assertThat(restored).isEqualTo(WindowBounds(1920, 600, maximized = true))
    }

    @Test
    fun `no screens leaves bounds alone`() {
        val bounds = WindowBounds(1280, 820, 5, 5)
        assertThat(fitToScreens(bounds, emptyList())).isEqualTo(bounds)
    }
}
