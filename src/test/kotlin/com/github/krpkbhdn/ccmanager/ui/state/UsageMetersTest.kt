package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UsageMetersTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun window(percent: Double, resetsInSeconds: Long) = RateLimitWindow(percent, now.plusSeconds(resetsInSeconds))

    @Test
    fun levelsSwitchAtSeventyAndNinety() {
        assertThat(listOf(0.0, 69.9, 70.0, 89.9, 90.0, 100.0).map(::usageLevel)).containsExactly(
            UsageLevel.NORMAL, UsageLevel.NORMAL, UsageLevel.HIGH, UsageLevel.HIGH, UsageLevel.CRITICAL, UsageLevel.CRITICAL,
        )
    }

    @Test
    fun resetTextUsesTheTwoLargestUnits() {
        assertThat(resetsInText(now.plusSeconds(9 * 60 + 30), now)).isEqualTo("resets in 9m")
        assertThat(resetsInText(now.plusSeconds(2 * 3600 + 14 * 60), now)).isEqualTo("resets in 2h 14m")
        assertThat(resetsInText(now.plusSeconds(3 * 86400 + 4 * 3600), now)).isEqualTo("resets in 3d 4h")
        assertThat(resetsInText(now.minusSeconds(5), now)).isEqualTo("resets now")
    }

    @Test
    fun metersCarryRoundedPercentAndExactTooltip() {
        val meters = usageMeters(RateLimitSnapshot(window(23.5, 8040), window(41.0, 86400 * 3), now), now)

        assertThat(meters.map { it.label to it.percentText }).containsExactly("5h" to "24%", "7d" to "41%")
        assertThat(meters[0].tooltip).isEqualTo("5-hour limit: 23.5% used, resets in 2h 14m")
        assertThat(meters[1].tooltip).isEqualTo("7-day limit: 41% used, resets in 3d 0h")
    }

    @Test
    fun missingOrExpiredWindowsAreLeftOut() {
        assertThat(usageMeters(null, now)).isEmpty()
        assertThat(usageMeters(RateLimitSnapshot(window(90.0, -1), window(5.0, 3600), now), now).map { it.label }).containsExactly("7d")
    }
}
