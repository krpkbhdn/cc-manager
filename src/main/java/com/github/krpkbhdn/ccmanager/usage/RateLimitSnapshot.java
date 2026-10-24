package com.github.krpkbhdn.ccmanager.usage;

import java.time.Instant;

/** Latest known usage limits; a window Claude did not report is null. */
public record RateLimitSnapshot(RateLimitWindow fiveHour, RateLimitWindow sevenDay, Instant receivedAt) {
}
