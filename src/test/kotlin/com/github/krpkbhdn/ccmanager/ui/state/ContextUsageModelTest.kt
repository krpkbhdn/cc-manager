package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.ContextWindow
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextUsageModelTest {

    private fun usage(percent: Double?, context: ContextWindow?) =
        SessionUsage("s", "Opus", "high", percent, null, null, Instant.EPOCH, context)

    @Test
    fun `full breakdown splits the window into parts and free space`() {
        val view = contextUsageView(usage(42.0, ContextWindow(200_000, 10_000, 4_000, 70_000, 1_500, 90_000, 8_000)))!!

        assertThat(view.percentText).isEqualTo("42%")
        assertThat(view.tokensText).isEqualTo("84k / 200k")
        assertThat(view.segments.map { it.part }).containsExactly(ContextPart.CACHE_READ, ContextPart.CACHE_WRITE, ContextPart.INPUT, ContextPart.FREE)
        assertThat(view.segments.first { it.part == ContextPart.FREE }.tokens).isEqualTo(116_000)
        assertThat(view.segments.sumOf { it.fraction.toDouble() }).isBetween(0.99, 1.0)
        assertThat(view.outputTokens).isEqualTo(1_500)
        assertThat(view.totalInputTokens).isEqualTo(90_000)
    }

    @Test
    fun `percent only keeps the bar and has no segments`() {
        val view = contextUsageView(usage(62.5, ContextWindow(200_000, null, null, null, null, 5L, 6L)))!!

        assertThat(view.percentText).isEqualTo("62.5%")
        assertThat(view.tokensText).isNull()
        assertThat(view.segments).isEmpty()
        assertThat(view.fraction).isEqualTo(0.625f)
    }

    @Test
    fun `percent is derived from tokens when claude sent none`() {
        val view = contextUsageView(usage(null, ContextWindow(200_000, 100_000, 0, 0, null, null, null)))!!

        assertThat(view.percentText).isEqualTo("50%")
    }

    @Test
    fun `no usage or nothing known gives no view`() {
        assertThat(contextUsageView(null)).isNull()
        assertThat(contextUsageView(usage(null, null))).isNull()
    }

    @Test
    fun `used above the window leaves no free space`() {
        val view = contextUsageView(usage(100.0, ContextWindow(1_000, 1_500, 0, 0, null, null, null)))!!

        assertThat(view.segments.first { it.part == ContextPart.FREE }.tokens).isZero()
    }

    @Test
    fun `percent is clamped between 0 and 100`() {
        val over = contextUsageView(usage(150.0, null))!!
        val under = contextUsageView(usage(-5.0, null))!!
        val zero = contextUsageView(usage(0.0, null))!!

        assertThat(over.percentText).isEqualTo("100%")
        assertThat(over.fraction).isEqualTo(1f)
        assertThat(over.level).isEqualTo(UsageLevel.CRITICAL)
        assertThat(under.fraction).isZero()
        assertThat(zero.percentText).isEqualTo("0%")
        assertThat(zero.level).isEqualTo(UsageLevel.NORMAL)
    }

    @Test
    fun `unknown or zero window size gives no token text and no segments`() {
        val unknown = contextUsageView(usage(10.0, ContextWindow(null, 100, 0, 0, null, null, null)))!!
        val zeroWindow = contextUsageView(usage(10.0, ContextWindow(0, 100, 0, 0, null, null, null)))!!

        assertThat(unknown.tokensText).isNull()
        assertThat(unknown.segments).isEmpty()
        assertThat(zeroWindow.tokensText).isNull()
        assertThat(zeroWindow.segments).isEmpty()
    }

    @Test
    fun `tokens without percent and without window give no view`() {
        assertThat(contextUsageView(usage(null, ContextWindow(null, 100, 0, 0, null, null, null)))).isNull()
    }

    @Test
    fun `tokens are abbreviated`() {
        assertThat(compactTokens(999)).isEqualTo("999")
        assertThat(compactTokens(1_200)).isEqualTo("1.2k")
        assertThat(compactTokens(200_000)).isEqualTo("200k")
        assertThat(compactTokens(1_500_000)).isEqualTo("1.5M")
    }

    @Test
    fun `level follows the thresholds`() {
        assertThat(contextUsageView(usage(49.9, null))!!.level).isEqualTo(UsageLevel.NORMAL)
        assertThat(contextUsageView(usage(50.1, null))!!.level).isEqualTo(UsageLevel.HIGH)
        assertThat(contextUsageView(usage(70.0, null))!!.level).isEqualTo(UsageLevel.HIGH)
        assertThat(contextUsageView(usage(70.1, null))!!.level).isEqualTo(UsageLevel.CRITICAL)
    }
}
