package com.github.krpkbhdn.ccmanager.ui.settings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PercentStepTest {
    @Test
    fun `typed number is committed within the bounds`() {
        assertThat(PercentStep.commit("125", 100, 50, 300)).isEqualTo(125)
        assertThat(PercentStep.commit("10", 100, 50, 300)).isEqualTo(50)
        assertThat(PercentStep.commit("9999", 100, 50, 300)).isEqualTo(300)
    }

    @Test
    fun `empty or non numeric input keeps the current value`() {
        assertThat(PercentStep.commit("", 133, 50, 300)).isEqualTo(133)
        assertThat(PercentStep.commit("abc", 133, 50, 300)).isEqualTo(133)
        assertThat(PercentStep.commit("-", 133, 50, 300)).isEqualTo(133)
    }

    @Test
    fun `non digits are dropped from typed text`() {
        assertThat(PercentStep.digitsOnly("1a2 5%")).isEqualTo("125")
        assertThat(PercentStep.commit("1a2 5%", 100, 50, 300)).isEqualTo(125)
    }

    @Test
    fun `arrows step from an off grid value and clamp at the bounds`() {
        assertThat(PercentStep.step(133, 10, 50, 300)).isEqualTo(143)
        assertThat(PercentStep.step(133, -10, 50, 300)).isEqualTo(123)
        assertThat(PercentStep.step(55, -10, 50, 300)).isEqualTo(50)
        assertThat(PercentStep.step(295, 10, 50, 300)).isEqualTo(300)
    }
}
