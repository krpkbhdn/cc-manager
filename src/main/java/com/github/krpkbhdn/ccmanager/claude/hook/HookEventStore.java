package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter;
import com.github.krpkbhdn.ccmanager.security.SecretFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(HookEventStore.class);
    private static final int PRUNE_EVERY_INSERTS = 100;
    private static final int OVER_FETCH_FACTOR = 4;
    /** Text is masked on this much more than is kept, so a secret straddling the kept limit is seen whole, while a huge body cannot stall the regexes. */
    private static final int MASK_LOOKAHEAD = 512;
    private static final String PARTIAL_TAIL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/_=.-";
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
    private final AtomicBoolean pruning = new AtomicBoolean();
    private final Executor pruner;

    @Autowired
    public HookEventStore(JdbcClient jdbc, JsonMapper mapper, HookProperties properties) {
        this(jdbc, mapper, properties, Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "hook-event-prune");
            thread.setDaemon(true);
            return thread;
        }));
    }

    HookEventStore(JdbcClient jdbc, JsonMapper mapper, HookProperties properties, Executor pruner) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
        this.pruner = pruner;
    }

    @PreDestroy
    void shutdown() {
        if (pruner instanceof ExecutorService service) {
            service.shutdownNow();
        }
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
            pruneInBackground();
        }
    }

    private void pruneInBackground() {
        if (!pruning.compareAndSet(false, true)) {
            return;
        }
        try {
            pruner.execute(() -> {
                try {
                    prune();
                } catch (RuntimeException e) {
                    log.warn("Could not prune hook events", e);
                } finally {
                    pruning.set(false);
                }
            });
        } catch (RuntimeException e) {
            pruning.set(false);
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
            return mapper.getNodeFactory().stringNode(maskedAndCut(node.asString()));
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

    /** A cut can split a secret the mask never saw whole, so after any cut the unfinished last token is dropped too. */
    private String maskedAndCut(String value) {
        var window = cut(value, properties.maxStoredTextLength() + MASK_LOOKAHEAD);
        var masked = SecretFilter.maskCommandText(window);
        var kept = cut(masked, properties.maxStoredTextLength());
        var truncated = window.length() < value.length() || masked.length() > kept.length();
        return truncated ? withoutPartialTail(kept) : kept;
    }

    private static String withoutPartialTail(String text) {
        var end = text.length();
        while (end > 0 && PARTIAL_TAIL.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }

    private static String cut(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
