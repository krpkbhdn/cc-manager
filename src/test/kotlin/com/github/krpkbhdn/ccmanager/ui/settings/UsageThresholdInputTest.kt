package com.github.krpkbhdn.ccmanager.ui.settings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UsageThresholdInputTest {
    @Test
    fun percentAcceptsZeroToHundred() {
        assertThat(UsageThresholdInput.percent("0")).isEqualTo(ThresholdInput.Valid(0))
        assertThat(UsageThresholdInput.percent(" 80 ")).isEqualTo(ThresholdInput.Valid(80))
        assertThat(UsageThresholdInput.percent("100")).isEqualTo(ThresholdInput.Valid(100))
    }

    @Test
    fun percentRejectsOutOfRangeAndNonNumbers() {
        listOf("-1", "101", "", "abc", "5.5").forEach {
            assertThat(UsageThresholdInput.percent(it)).isInstanceOf(ThresholdInput.Invalid::class.java)
        }
    }

    @Test
    fun budgetAcceptsDecimalsWithDotOrComma() {
        assertThat(UsageThresholdInput.budget("0")).isEqualTo(ThresholdInput.Valid(0.0))
        assertThat(UsageThresholdInput.budget("12.5")).isEqualTo(ThresholdInput.Valid(12.5))
        assertThat(UsageThresholdInput.budget("7,25")).isEqualTo(ThresholdInput.Valid(7.25))
    }

    @Test
    fun budgetRejectsNegativeAndNonFinite() {
        listOf("-0.5", "", "abc", "NaN", "Infinity").forEach {
            assertThat(UsageThresholdInput.budget(it)).isInstanceOf(ThresholdInput.Invalid::class.java)
        }
    }
}
