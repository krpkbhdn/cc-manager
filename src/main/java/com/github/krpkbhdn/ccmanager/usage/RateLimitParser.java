package com.github.krpkbhdn.ccmanager.usage;

import java.time.Instant;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Reads the rate_limits field of a statusLine payload, skipping absent, malformed and already reset windows. */
final class RateLimitParser {

    private static final String FIVE_HOUR = "five_hour";
    private static final String SEVEN_DAY = "seven_day";

    private RateLimitParser() {
    }

    static Optional<RateLimitSnapshot> parse(JsonNode payload, Instant receivedAt) {
        var limits = payload.path("rate_limits");
        var fiveHour = window(limits.path(FIVE_HOUR), receivedAt);
        var sevenDay = window(limits.path(SEVEN_DAY), receivedAt);
        if (fiveHour == null && sevenDay == null) {
            return Optional.empty();
        }
        return Optional.of(new RateLimitSnapshot(fiveHour, sevenDay, receivedAt));
    }

    private static RateLimitWindow window(JsonNode node, Instant now) {
        if (!node.path("used_percentage").isNumber() || !node.path("resets_at").isNumber()) {
            return null;
        }
        var resetsAt = Instant.ofEpochSecond(node.path("resets_at").asLong());
        return resetsAt.isAfter(now) ? new RateLimitWindow(node.path("used_percentage").asDouble(), resetsAt) : null;
    }
}
