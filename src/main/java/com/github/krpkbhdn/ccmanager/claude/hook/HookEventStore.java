package com.github.krpkbhdn.ccmanager.claude.hook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Persistent log of all hook events (SQLite), capped by retention. Long strings are truncated before storing. */
@Component
@EnableConfigurationProperties(HookProperties.class)
public class HookEventStore {

    private static final int PRUNE_EVERY_INSERTS = 100;
    private static final int OVER_FETCH_FACTOR = 4;

    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    private final HookProperties properties;
    private final AtomicInteger insertsSincePrune = new AtomicInteger();

    public HookEventStore(JdbcClient jdbc, JsonMapper mapper, HookProperties properties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
    }

    public void save(HookEvent event) {
        jdbc.sql("""
                        INSERT INTO hook_event (ccm_session_id, event, agent_id, claude_session_id, payload, received_at)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .params(event.ccmSessionId(), event.rawName(), event.agentId(), event.text("session_id"),
                        mapper.writeValueAsString(truncated(event.payload())), event.receivedAt().toString())
                .update();
        if (insertsSincePrune.incrementAndGet() >= PRUNE_EVERY_INSERTS) {
            insertsSincePrune.set(0);
            prune();
        }
    }

    /** Newest last. */
    public List<ActivityEntry> recent(String ccmSessionId, int limit) {
        var entries = new ArrayList<>(jdbc.sql("""
                        SELECT event, payload, received_at FROM hook_event
                        WHERE ccm_session_id = ? ORDER BY id DESC LIMIT ?""")
                .params(ccmSessionId, limit * OVER_FETCH_FACTOR)
                .query((rs, row) -> new HookEvent(ccmSessionId, HookEventType.of(rs.getString("event")),
                        rs.getString("event"), mapper.readTree(rs.getString("payload")),
                        Instant.parse(rs.getString("received_at"))))
                .list()
                .stream()
                .flatMap(event -> ActivityFormatter.describe(event)
                        .map(text -> new ActivityEntry(event.receivedAt(), event.type(), nullIfEmpty(event.agentId()), text))
                        .stream())
                .limit(limit)
                .toList());
        Collections.reverse(entries);
        return List.copyOf(entries);
    }

    void prune() {
        jdbc.sql("DELETE FROM hook_event WHERE id <= (SELECT max(id) FROM hook_event) - ?")
                .param(properties.retention())
                .update();
    }

    private JsonNode truncated(JsonNode node) {
        if (node.isString()) {
            return mapper.getNodeFactory().stringNode(cut(node.asString()));
        }
        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            node.properties().forEach(field -> copy.set(field.getKey(), truncated(field.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            node.forEach(element -> copy.add(truncated(element)));
            return copy;
        }
        return node;
    }

    private String cut(String value) {
        return value.length() <= properties.maxStoredTextLength()
                ? value
                : value.substring(0, properties.maxStoredTextLength());
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
