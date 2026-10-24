package com.github.krpkbhdn.ccmanager.usage;

import java.time.Instant;

/** One subscription usage window: percent of the allowance used (0-100) and when it resets. */
public record RateLimitWindow(double usedPercent, Instant resetsAt) {
}
