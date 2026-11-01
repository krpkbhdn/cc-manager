package com.github.krpkbhdn.ccmanager.ui.usage

import com.github.krpkbhdn.ccmanager.ui.state.UsageLevel
import com.github.krpkbhdn.ccmanager.ui.state.UsageWindowDetail
import com.github.krpkbhdn.ccmanager.usage.history.DailyCost
import com.github.krpkbhdn.ccmanager.usage.history.PeriodSummary
import java.time.LocalDate
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UsageScreenModelTest {
    private val today = LocalDate.of(2026, 5, 10)
    private val zone = ZoneId.of("UTC")
    private val summary = PeriodSummary(6.0, 3, 10)

    private fun cost(day: Int, project: Long, usd: Double, model: String = "opus") = DailyCost(LocalDate.of(2026, 5, day), project, model, usd)

    private fun build(costs: List<DailyCost>, period: UsagePeriod = UsagePeriod.WEEK, top: Int = TOP_PROJECTS) =
        buildUsage(period, rangeOf(period, today, zone), costs, summary, emptyList(), top)

    @Test
    fun `range covers the last N local days including today`() {
        val range = rangeOf(UsagePeriod.WEEK, today, zone)

        assertThat(range.firstDay).isEqualTo(LocalDate.of(2026, 5, 4))
        assertThat(range.lastDay).isEqualTo(today)
        assertThat(range.from).isEqualTo(LocalDate.of(2026, 5, 4).atStartOfDay(zone).toInstant())
        assertThat(range.to).isEqualTo(LocalDate.of(2026, 5, 11).atStartOfDay(zone).toInstant())
    }

    @Test
    fun `days without cost are filled with zero`() {
        val data = build(listOf(cost(6, 1, 2.0)))

        assertThat(data.days).hasSize(7)
        assertThat(data.days.map { it.totalUsd }).containsExactly(0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0)
    }

    @Test
    fun `project totals sum the period and sort by cost`() {
        val totals = projectTotals(listOf(cost(5, 1, 1.0), cost(6, 2, 3.0), cost(7, 1, 1.5)))

        assertThat(totals).containsExactly(ProjectSlice(2, 3.0), ProjectSlice(1, 2.5))
    }

    @Test
    fun `projects beyond the top N are merged into other`() {
        val data = build(listOf(cost(5, 1, 5.0), cost(5, 2, 3.0), cost(5, 3, 1.0), cost(6, 4, 0.5)), top = 2)

        assertThat(data.projects).containsExactly(ProjectSlice(1, 5.0), ProjectSlice(2, 3.0), ProjectSlice(null, 1.5))
        assertThat(data.days[1].slices).containsExactly(ProjectSlice(1, 5.0), ProjectSlice(2, 3.0), ProjectSlice(null, 1.0))
    }

    @Test
    fun `unknown model is named and models sort by cost`() {
        val models = modelTotals(listOf(cost(5, 1, 1.0, ""), cost(5, 1, 2.0, "opus")))

        assertThat(models.map { it.model }).containsExactly("opus", "Unknown")
    }

    @Test
    fun `limit tiles appear only when limits are reported`() {
        val data = build(listOf(cost(5, 1, 6.0)))
        val limit = UsageWindowDetail("7-day limit", 0.4f, UsageLevel.NORMAL, "40%", "60% left", "in 2d", "Mon")

        assertThat(summaryTiles(data, emptyList()).map { it.title }).containsExactly("Total cost", "Sessions", "Average per day")
        assertThat(summaryTiles(data, listOf(limit)).last().value).isEqualTo("40%")
    }

    @Test
    fun `project labels cover the assistant and removed projects`() {
        assertThat(projectLabel(0) { "x" }).isEqualTo("Assistant")
        assertThat(projectLabel(7) { null }).isEqualTo("Project 7")
        assertThat(projectLabel(7) { "Shop" }).isEqualTo("Shop")
    }
}
