package com.github.krpkbhdn.ccmanager.ui.usage

import com.github.krpkbhdn.ccmanager.ui.state.UsageWindowDetail
import com.github.krpkbhdn.ccmanager.ui.state.formatCost
import com.github.krpkbhdn.ccmanager.usage.history.DailyCost
import com.github.krpkbhdn.ccmanager.usage.history.PeriodSummary
import com.github.krpkbhdn.ccmanager.usage.history.SessionCost
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val ASSISTANT_PROJECT_ID = 0L
private const val UNKNOWN_MODEL = "Unknown"
const val TOP_PROJECTS = 5
const val TOP_SESSIONS = 10

enum class UsagePeriod(val days: Int, val label: String) { WEEK(7, "7 days"), MONTH(30, "30 days"), QUARTER(90, "90 days") }

/** The half-open instant range [from, to) covering the last [UsagePeriod.days] local days including today. */
data class UsageRange(val from: Instant, val to: Instant, val firstDay: LocalDate, val lastDay: LocalDate)

fun rangeOf(period: UsagePeriod, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): UsageRange {
    val first = today.minusDays(period.days - 1L)
    return UsageRange(first.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant(), first, today)
}

/** One stacked segment; a null [projectId] is the "other" bucket of the projects beyond the top ones. */
data class ProjectSlice(val projectId: Long?, val costUsd: Double)

data class DayBar(val day: LocalDate, val slices: List<ProjectSlice>) {
    val totalUsd: Double get() = slices.sumOf { it.costUsd }
}

data class ModelTotal(val model: String, val costUsd: Double)

data class SummaryTile(val title: String, val value: String)

data class UsageData(
    val period: UsagePeriod,
    val days: List<DayBar>,
    val projects: List<ProjectSlice>,
    val models: List<ModelTotal>,
    val sessions: List<SessionCost>,
    val summary: PeriodSummary,
) {
    val empty: Boolean get() = summary.totalCostUsd() <= 0.0 && days.none { it.totalUsd > 0.0 }
}

/** Every day of the range, also the ones without cost, with the projects outside the top [topProjects] merged into "other". */
fun buildUsage(period: UsagePeriod, range: UsageRange, costs: List<DailyCost>, summary: PeriodSummary, sessions: List<SessionCost>, topProjects: Int = TOP_PROJECTS): UsageData {
    val shown = projectTotals(costs).take(topProjects).mapNotNull { it.projectId }.toSet()
    val byDay = costs.groupBy { it.day() }
    val days = generateSequence(range.firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(range.lastDay) }.map { day ->
        DayBar(day, stack(byDay[day].orEmpty(), shown))
    }.toList()
    return UsageData(period, days, stack(costs, shown), modelTotals(costs), sessions, summary)
}

/** Cost per project, the most expensive first. */
fun projectTotals(costs: List<DailyCost>): List<ProjectSlice> =
    costs.groupBy { it.projectId() }.map { (id, rows) -> ProjectSlice(id, rows.sumOf { it.costUsd() }) }.sortedByDescending { it.costUsd }

fun modelTotals(costs: List<DailyCost>): List<ModelTotal> =
    costs.groupBy { it.model().ifBlank { UNKNOWN_MODEL } }.map { (model, rows) -> ModelTotal(model, rows.sumOf { it.costUsd() }) }.sortedByDescending { it.costUsd }

private fun stack(costs: List<DailyCost>, shown: Set<Long>): List<ProjectSlice> {
    val (top, rest) = projectTotals(costs).partition { it.projectId in shown }
    val other = rest.sumOf { it.costUsd }
    return top + if (other > 0.0) listOf(ProjectSlice(null, other)) else emptyList()
}

/** Cost, session and average tiles; the limit tiles exist only when Claude reports limits, so Team plans show none. */
fun summaryTiles(data: UsageData, limits: List<UsageWindowDetail>): List<SummaryTile> = listOf(
    SummaryTile("Total cost", formatCost(data.summary.totalCostUsd())),
    SummaryTile("Sessions", data.summary.sessionCount().toString()),
    SummaryTile("Average per day", formatCost(data.summary.totalCostUsd() / data.period.days)),
) + limits.map { SummaryTile(it.title, it.usedText) }

fun projectLabel(projectId: Long, knownName: (Long) -> String?): String =
    if (projectId == ASSISTANT_PROJECT_ID) "Assistant" else knownName(projectId) ?: "Project $projectId"
