package com.github.krpkbhdn.ccmanager.usage.history;

/** Usage alert limits; 0 turns the matching alert off. */
public record UsageThresholds(int fiveHourPercent, int sevenDayPercent, double dailyBudgetUsd) {
}
