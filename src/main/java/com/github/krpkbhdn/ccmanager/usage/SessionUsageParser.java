package com.github.krpkbhdn.ccmanager.usage;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Reads model, effort, context window and cost from a statusLine payload, tolerating any missing or mistyped field. */
final class SessionUsageParser {

    private SessionUsageParser() {
    }

    static SessionUsage parse(String ccmSessionId, JsonNode payload, Instant receivedAt) {
        var model = payload.path("model");
        var modelName = text(model.path("display_name"));
        return new SessionUsage(ccmSessionId,
                modelName != null ? modelName : text(model.path("id")),
                text(payload.path("effort").path("level")),
                number(payload.path("context_window").path("used_percentage")),
                number(payload.path("cost").path("total_cost_usd")),
                millis(payload.path("cost").path("total_duration_ms")),
                receivedAt);
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString() : null;
    }

    private static Double number(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
    }

    private static Long millis(JsonNode node) {
        return node.isNumber() ? node.asLong() : null;
    }
}
