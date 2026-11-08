package com.github.krpkbhdn.ccmanager.ui.usage

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UsageChartHitTest {
    private val days = listOf(
        DayBar(LocalDate.of(2026, 5, 1), listOf(ProjectSlice(1, 6.0), ProjectSlice(null, 2.0))),
        DayBar(LocalDate.of(2026, 5, 2), emptyList()),
    )
    private val peak = 8.0

    // width 200 -> slot 100, bar from 15 to 85; height 100 -> first slice 75 tall (y 25..100), second 25 (y 0..25)
    private fun hit(x: Float, y: Float) = dayHit(days, x, y, 200f, 100f, peak)

    @Test
    fun `pointer inside a bar selects its day and the segment under it`() {
        assertThat(hit(50f, 90f)).isEqualTo(DayHit(0, 0))
        assertThat(hit(50f, 10f)).isEqualTo(DayHit(0, 1))
    }

    @Test
    fun `gap between bars and outside the chart hit nothing`() {
        assertThat(hit(90f, 50f)).isNull()
        assertThat(hit(5f, 50f)).isNull()
        assertThat(hit(-1f, 50f)).isNull()
        assertThat(hit(200f, 50f)).isNull()
        assertThat(hit(50f, 101f)).isNull()
    }

    @Test
    fun `a zero-cost day has no bar and no hit`() {
        assertThat(hit(150f, 50f)).isNull()
        assertThat(hit(150f, 100f)).isNull()
    }

    @Test
    fun `above the bar top and below the baseline hit nothing`() {
        val short = listOf(DayBar(LocalDate.of(2026, 5, 1), listOf(ProjectSlice(1, 2.0))))
        assertThat(dayHit(short, 50f, 10f, 100f, 100f, peak)).isNull()
        assertThat(dayHit(short, 50f, 80f, 100f, 100f, peak)).isEqualTo(DayHit(0, 0))
        assertThat(dayHit(short, 50f, 100.5f, 100f, 100f, peak)).isNull()
    }

    @Test
    fun `model bar hits only its filled length and thickness`() {
        assertThat(modelBarHit(10f, 5f, 200f, 10f, 4.0, 8.0)).isTrue()
        assertThat(modelBarHit(101f, 5f, 200f, 10f, 4.0, 8.0)).isFalse()
        assertThat(modelBarHit(-1f, 5f, 200f, 10f, 4.0, 8.0)).isFalse()
        assertThat(modelBarHit(10f, 11f, 200f, 10f, 4.0, 8.0)).isFalse()
        assertThat(modelBarHit(0f, 5f, 200f, 10f, 0.0, 8.0)).isFalse()
    }

    @Test
    fun `day tooltip lists total then segments in stacked order with other`() {
        val tooltip = dayTooltip(days[0]) { "Project $it" }
        assertThat(tooltip.title).isEqualTo("Fri, 1 May 2026")
        assertThat(tooltip.rows.map { it.label to it.value }).containsExactly(
            "Total" to "$8.0000",
            "Project 1" to "$6.0000",
            "Other" to "$2.0000",
        )
    }

    @Test
    fun `an empty day shows a plain zero total`() {
        assertThat(dayTooltip(days[1]) { "p" }.rows).containsExactly(TooltipRow("Total", "$0.00"))
    }

    @Test
    fun `model tooltip shows exact cost and share`() {
        val models = listOf(ModelTotal("opus", 12.3456), ModelTotal("haiku", 3.1544))
        val tooltip = modelTooltip(models[0], models)
        assertThat(tooltip.title).isEqualTo("opus")
        assertThat(tooltip.rows.map { it.value }).containsExactly("$12.3456", "79.6%")
    }

    @Test
    fun `tooltip flips and clamps inside the bounds`() {
        assertThat(tooltipOrigin(10f, 10f, 50f, 20f, 200f, 100f, 5f)).isEqualTo(15f to 15f)
        assertThat(tooltipOrigin(190f, 95f, 50f, 20f, 200f, 100f, 5f)).isEqualTo(135f to 70f)
        assertThat(tooltipOrigin(10f, 10f, 300f, 200f, 200f, 100f, 5f)).isEqualTo(0f to 0f)
    }
}
