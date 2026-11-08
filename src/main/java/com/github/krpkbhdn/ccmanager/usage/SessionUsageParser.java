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
                wholeNumber(payload.path("cost").path("total_duration_ms")),
                receivedAt,
                contextWindow(payload.path("context_window")));
    }

    private static ContextWindow contextWindow(JsonNode window) {
        if (!window.isObject()) {
            return null;
        }
        var current = window.path("current_usage");
        var context = new ContextWindow(wholeNumber(window.path("context_window_size")),
                wholeNumber(current.path("input_tokens")),
                wholeNumber(current.path("cache_creation_input_tokens")),
                wholeNumber(current.path("cache_read_input_tokens")),
                wholeNumber(current.path("output_tokens")),
                wholeNumber(window.path("total_input_tokens")),
                wholeNumber(window.path("total_output_tokens")));
        return context.equals(new ContextWindow(null, null, null, null, null, null, null)) ? null : context;
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString() : null;
    }

    private static Double number(JsonNode node) {
        return node.isNumber() ? node.asDouble() : null;
    }

    private static Long wholeNumber(JsonNode node) {
        return node.isNumber() ? node.asLong() : null;
    }
}
