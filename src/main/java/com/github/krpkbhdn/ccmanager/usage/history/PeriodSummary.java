package com.github.krpkbhdn.ccmanager.usage.history;

/** Totals of a period; sessionCount is summed per day for aggregated days, so a session spanning days counts twice. */
public record PeriodSummary(double totalCostUsd, long sessionCount, long sampleCount) {
}
