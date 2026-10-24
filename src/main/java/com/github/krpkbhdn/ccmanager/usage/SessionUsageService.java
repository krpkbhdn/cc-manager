package com.github.krpkbhdn.ccmanager.usage;

import com.github.krpkbhdn.ccmanager.claude.hook.StatusLineReport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps the newest statusLine figures of every session, for the usage panel and the session header. Thread-safe. */
@Component
public class SessionUsageService {

    private final Map<String, SessionUsage> bySession = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @EventListener
    public void on(StatusLineReport report) {
        bySession.put(report.ccmSessionId(), SessionUsageParser.parse(report.ccmSessionId(), report.payload(), report.receivedAt()));
        listeners.forEach(Runnable::run);
    }

    public Optional<SessionUsage> of(String ccmSessionId) {
        return Optional.ofNullable(bySession.get(ccmSessionId));
    }

    public Map<String, SessionUsage> all() {
        return Map.copyOf(bySession);
    }

    /** @return unsubscribe handle; callback fires (any thread) after every report */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }
}
