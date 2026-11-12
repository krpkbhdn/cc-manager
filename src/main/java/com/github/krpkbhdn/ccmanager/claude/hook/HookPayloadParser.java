package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns a hook request body into a JSON object, salvaging only the event name from a body cut at the size cap. */
final class HookPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(HookPayloadParser.class);
    private static final String EVENT_NAME_FIELD = "hook_event_name";
    private static final Duration WARN_INTERVAL = Duration.ofSeconds(30);

    private final JsonMapper mapper;
    private final HookProperties properties;
    private final AtomicLong oversizedWarnNanos = new AtomicLong(System.nanoTime() - WARN_INTERVAL.toNanos());

    HookPayloadParser(JsonMapper mapper, HookProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    Optional<JsonNode> parseObject(String ccmSessionId, byte[] body) {
        if (body.length > properties.maxBodyBytes()) {
            return salvageEventName(ccmSessionId, body);
        }
        try {
            JsonNode payload = mapper.readTree(body);
            return payload.isObject() ? Optional.of(payload) : Optional.empty();
        } catch (RuntimeException e) {
            log.debug("Ignored unusable hook call for session {}: {}", ccmSessionId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * The body is cut at the cap, so it is no valid JSON; a field before the cut still tells which event it was, so statuses keep working.
     * A decision event is dropped instead (the terminal dialog stays in charge): its truncated stub would leave NEEDS_ACTION without a proposal.
     */
    private Optional<JsonNode> salvageEventName(String ccmSessionId, byte[] truncatedBody) {
        var name = readEventName(truncatedBody);
        if (name.isEmpty() || HookEventType.of(name.get()).awaitsDecision()) {
            warnRarely(oversizedWarnNanos, "Dropped an oversized hook body of session {} ({})", ccmSessionId,
                    name.isEmpty() ? "event name not before the cap" : "it asks for a decision");
            return Optional.empty();
        }
        warnRarely(oversizedWarnNanos, "Oversized hook body of session {}: only its event name is kept ({})", ccmSessionId, name.get());
        return Optional.of(mapper.createObjectNode().put(EVENT_NAME_FIELD, name.get()));
    }

    private Optional<String> readEventName(byte[] truncatedBody) {
        try (var parser = mapper.createParser(truncatedBody)) {
            if (parser.nextToken() == JsonToken.START_OBJECT) {
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    var field = parser.currentName();
                    if (parser.nextToken() == JsonToken.VALUE_STRING && EVENT_NAME_FIELD.equals(field)) {
                        return Optional.of(parser.getString());
                    }
                    parser.skipChildren();
                }
            }
        } catch (RuntimeException e) {
            log.debug("Oversized hook body ends inside a field: {}", e.toString());
        }
        return Optional.empty();
    }

    /** A session producing many oversized bodies must not flood the log. */
    private static void warnRarely(AtomicLong lastNanos, String message, Object first, Object second) {
        var now = System.nanoTime();
        var last = lastNanos.get();
        if (now - last >= WARN_INTERVAL.toNanos() && lastNanos.compareAndSet(last, now)) {
            log.warn(message + " (repeats are logged at debug)", first, second);
        } else {
            log.debug(message, first, second);
        }
    }
}
