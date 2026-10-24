package com.github.krpkbhdn.ccmanager.usage;

import java.time.Instant;

/** Per-session figures from the latest statusLine report; a figure Claude did not send is null. */
public record SessionUsage(String ccmSessionId, String modelName, String effort, Double contextUsedPercent,
        Double costUsd, Long durationMillis, Instant receivedAt) {
}
