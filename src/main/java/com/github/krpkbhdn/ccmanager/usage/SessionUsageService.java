package com.github.krpkbhdn.ccmanager.usage;

import com.github.krpkbhdn.ccmanager.claude.hook.StatusLineReport;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps the newest statusLine figures of every live session, for the usage panel and the session header. Thread-safe. */
@Component
public class SessionUsageService {

    private final Map<String, SessionUsage> bySession = new ConcurrentHashMap<>();
    private final ChangeListeners listeners = new ChangeListeners("Session usage");
    private final SessionRegistry sessions;

    public SessionUsageService(SessionRegistry sessions) {
        this.sessions = sessions;
        sessions.subscribe(this::dropEndedSessions);
    }

    @EventListener
    public void on(StatusLineReport report) {
        if (sessions.find(report.ccmSessionId()).isEmpty()) {
            return;
        }
        bySession.put(report.ccmSessionId(), SessionUsageParser.parse(report.ccmSessionId(), report.payload(), report.receivedAt()));
        listeners.fire();
    }

    public Optional<SessionUsage> of(String ccmSessionId) {
        return Optional.ofNullable(bySession.get(ccmSessionId));
    }

    public Map<String, SessionUsage> all() {
        return Map.copyOf(bySession);
    }

    /** @return unsubscribe handle; callback fires (any thread) after every report */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private void dropEndedSessions() {
        bySession.keySet().removeIf(id -> sessions.find(id).isEmpty());
    }
}
