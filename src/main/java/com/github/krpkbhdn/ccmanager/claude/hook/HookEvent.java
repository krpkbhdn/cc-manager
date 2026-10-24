package com.github.krpkbhdn.ccmanager.claude.hook;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * One hook call received from a Claude Code process. ccmSessionId is the id this app assigned to the process
 * (not Claude's own session_id, which lives in the payload and changes on /clear).
 */
public record HookEvent(String ccmSessionId, HookEventType type, String rawName, JsonNode payload, Instant receivedAt) {

    /** String payload field, empty when absent. */
    public String text(String field) {
        return payload.path(field).asString("");
    }

    /** Number of elements of an array payload field, 0 when absent. */
    public int count(String field) {
        return payload.path(field).size();
    }

    /** Id of the subagent that raised the event, empty for the main agent. */
    public String agentId() {
        return text("agent_id");
    }

    public boolean fromSubagent() {
        return !agentId().isEmpty();
    }
}
