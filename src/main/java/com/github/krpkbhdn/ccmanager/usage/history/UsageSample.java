package com.github.krpkbhdn.ccmanager.usage.history;

import java.time.Instant;

/** One statusLine report of a session; costDelta is the cost added since that session's previous sample. */
public record UsageSample(Instant at, String ccmSessionId, String claudeSessionId, long projectId, String model,
        String effort, Double contextPercent, double costUsd, double costDelta) {
}
