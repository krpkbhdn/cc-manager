package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WheelLinesTest {
    private val wheel = WheelLines()

    @Test
    fun firstEventOfAGestureIsOneLineEvenWhenTiny() {
        assertThat(wheel.take(0.01f, 1000)).isEqualTo(1)
        assertThat(wheel.take(-0.01f, 3000)).isEqualTo(-1)
    }

    @Test
    fun burstIsLimitedToOneLinePerInterval() {
        wheel.take(0.9f, 1000)
        val lines = (1..10).map { wheel.take(0.9f, 1000L + it * 16) }
        assertThat(lines.sum()).isLessThanOrEqualTo(3)
        assertThat(lines.all { it in 0..1 }).isTrue()
    }

    @Test
    fun slowDriftAccumulatesBeforeReporting() {
        wheel.take(0.1f, 1000)
        assertThat(wheel.take(0.4f, 1100)).isEqualTo(0)
        assertThat(wheel.take(0.4f, 1200)).isEqualTo(0)
        assertThat(wheel.take(0.4f, 1300)).isEqualTo(1)
    }

    @Test
    fun aNotchReportsOnEveryEventWithoutThrottling() {
        assertThat((0..4).map { wheel.take(1f, 1000L + it * 5) }).containsOnly(1)
        assertThat(wheel.take(-1f, 1030)).isEqualTo(-1)
    }

    @Test
    fun aLargeNotchIsCapped() {
        assertThat(wheel.take(40f, 1000)).isEqualTo(5)
    }

    @Test
    fun reversingDirectionDropsTheRemainder() {
        wheel.take(0.1f, 1000)
        wheel.take(0.5f, 1100)
        assertThat(wheel.take(-0.5f, 1200)).isEqualTo(0)
        assertThat(wheel.take(-0.5f, 1300)).isEqualTo(-1)
    }
}
