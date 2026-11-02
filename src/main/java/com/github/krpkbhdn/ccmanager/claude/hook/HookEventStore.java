package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
    /** Pruning keeps a session's newest retention / this many rows (at least one), but only rows younger than the max age, so the table stays bounded. */
    private static final int SESSION_FLOOR_DIVISOR = 100;
    private static final Duration SESSION_FLOOR_MAX_AGE = Duration.ofDays(7);
    private static final String TOOL_INPUT = "tool_input";
    private static final String TOOL_RESPONSE = "tool_response";
    private static final Set<String> READ_TOOL_INPUT_FIELDS =
            Set.of("command", "file_path", "notebook_path", "pattern", "url", "query", "description");

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
                        mapper.writeValueAsString(storable(event.payload())), event.receivedAt().toString())
                .update();
        if (insertsSincePrune.incrementAndGet() >= PRUNE_EVERY_INSERTS) {
            insertsSincePrune.set(0);
            prune();
        }
    }

    /** Newest last; pages back through older rows until {@code limit} events have a description or maxScannedEvents rows were read. */
    public List<ActivityEntry> recent(String ccmSessionId, int limit) {
        var entries = new ArrayList<ActivityEntry>();
        int pageSize = limit * OVER_FETCH_FACTOR;
        long beforeId = Long.MAX_VALUE;
        for (int scanned = 0; entries.size() < limit && scanned < properties.maxScannedEvents(); scanned += pageSize) {
            var page = fetchPage(ccmSessionId, pageSize, beforeId);
            page.stream()
                    .map(StoredEvent::event)
                    .flatMap(event -> ActivityFormatter.describe(event)
                            .map(text -> new ActivityEntry(event.receivedAt(), event.type(), nullIfEmpty(event.agentId()), text))
                            .stream())
                    .limit(limit - entries.size())
                    .forEach(entries::add);
            if (page.size() < pageSize) {
                break;
            }
            beforeId = page.getLast().id();
        }
        Collections.reverse(entries);
        return List.copyOf(entries);
    }

    private record StoredEvent(long id, HookEvent event) {
    }

    private List<StoredEvent> fetchPage(String ccmSessionId, int pageSize, long beforeId) {
        return jdbc.sql("""
                        SELECT id, event, payload, received_at FROM hook_event
                        WHERE ccm_session_id = ? AND id < ? ORDER BY id DESC LIMIT ?""")
                .params(ccmSessionId, beforeId, pageSize)
                .query((rs, row) -> new StoredEvent(rs.getLong("id"),
                        new HookEvent(ccmSessionId, HookEventType.of(rs.getString("event")),
                                rs.getString("event"), mapper.readTree(rs.getString("payload")),
                                Instant.parse(rs.getString("received_at")))))
                .list();
    }

    void prune() {
        jdbc.sql("""
                        DELETE FROM hook_event WHERE id <= (SELECT max(id) FROM hook_event) - ?
                        AND (received_at < ? OR id < COALESCE((SELECT e.id FROM hook_event e
                            WHERE e.ccm_session_id = hook_event.ccm_session_id ORDER BY e.id DESC LIMIT 1 OFFSET ?), 0))""")
                .params(properties.retention(), Instant.now().minus(SESSION_FLOOR_MAX_AGE).toString(),
                        properties.retention() / SESSION_FLOOR_DIVISOR)
                .update();
    }

    /** Only what ActivityFormatter reads survives: no tool output, no file contents or edits; secret-looking values are masked. */
    private JsonNode storable(JsonNode payload) {
        ObjectNode copy = mapper.createObjectNode();
        payload.properties().stream()
                .filter(field -> !field.getKey().equals(TOOL_RESPONSE))
                .forEach(field -> copy.set(field.getKey(), field.getKey().equals(TOOL_INPUT)
                        ? truncated(withoutUnreadFields(field.getValue()))
                        : truncated(field.getValue())));
        return copy;
    }

    private JsonNode withoutUnreadFields(JsonNode toolInput) {
        ObjectNode kept = mapper.createObjectNode();
        toolInput.properties().stream()
                .filter(field -> READ_TOOL_INPUT_FIELDS.contains(field.getKey()))
                .forEach(field -> kept.set(field.getKey(), field.getValue()));
        return kept;
    }

    private JsonNode truncated(JsonNode node) {
        if (node.isString()) {
            return mapper.getNodeFactory().stringNode(SecretPatterns.KEY_VALUE.matcher(cut(node.asString())).replaceAll("$1[redacted]"));
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
