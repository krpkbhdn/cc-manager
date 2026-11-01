package com.github.krpkbhdn.ccmanager.usage.history;

import java.time.Instant;

/** Account usage windows at one moment; a window Claude did not report (Team plans) is null. */
public record RateLimitSample(Instant at, Double fiveHourPercent, Instant fiveHourResetsAt, Double sevenDayPercent,
        Instant sevenDayResetsAt) {
}
