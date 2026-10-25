package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WheelLinesTest {
    private val wheel = WheelLines()

    @Test
    fun fractionalDeltasAccumulateIntoWholeLines() {
        val lines = List(10) { wheel.take(0.25f) }
        assertThat(lines).containsExactly(0, 0, 0, 1, 0, 0, 0, 1, 0, 0)
    }

    @Test
    fun aFullNotchIsOneLine() {
        assertThat(wheel.take(1f)).isEqualTo(1)
        assertThat(wheel.take(-1f)).isEqualTo(-1)
    }

    @Test
    fun reversingDirectionDropsTheRemainder() {
        wheel.take(0.75f)
        assertThat(wheel.take(-0.5f)).isEqualTo(0)
        assertThat(wheel.take(-0.5f)).isEqualTo(-1)
    }
}
