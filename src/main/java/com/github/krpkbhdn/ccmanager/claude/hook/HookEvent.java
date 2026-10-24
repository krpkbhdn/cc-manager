package com.github.krpkbhdn.ccmanager.claude.hook;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * One hook call received from a Claude Code process. ccmSessionId is the id this app assigned to the process
 * (not Claude's own session_id, which lives in the payload and changes on /clear).
 */
public record HookEvent(String ccmSessionId, HookEventType type, String rawName, JsonNode payload, Instant receivedAt) {}
