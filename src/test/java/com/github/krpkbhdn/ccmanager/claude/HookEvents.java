package com.github.krpkbhdn.ccmanager.claude;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

/** Builds hook events for tests; every event is one second after the previous one. */
public final class HookEvents {

    public static final String CCM_ID = "s1";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final AtomicInteger CLOCK = new AtomicInteger();

    private HookEvents() {}

    /** @param json payload fields without hook_event_name, e.g. {"tool_name":"Bash"} */
    public static HookEvent of(String eventName, String json) {
        var payload = MAPPER.readTree(json);
        return new HookEvent(CCM_ID, HookEventType.of(eventName), eventName, payload, EPOCH.plusSeconds(CLOCK.incrementAndGet()));
    }

    public static HookEvent of(String eventName) {
        return of(eventName, "{}");
    }
}
