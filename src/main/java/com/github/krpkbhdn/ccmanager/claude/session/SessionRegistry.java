package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Live sessions and their status, derived from hook events and process lifecycle. Thread-safe. */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final Map<String, SessionState> states = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Creates a live session in IDLE state and returns its ccm id. */
    public String register(long projectId) {
        var ccmSessionId = UUID.randomUUID().toString();
        states.put(ccmSessionId, SessionState.initial(ccmSessionId, projectId, Instant.now()));
        notifyListeners();
        return ccmSessionId;
    }

    /** Process exited: the session leaves the registry. */
    public void markEnded(String ccmSessionId, int exitCode) {
        log.debug("Session {} ended with exit code {}", ccmSessionId, exitCode);
        if (states.remove(ccmSessionId) != null) {
            notifyListeners();
        }
    }

    /** User pressed Esc in the terminal: Claude fires no Stop on interrupt, so WORKING becomes IDLE here. */
    public void markInterrupted(String ccmSessionId) {
        update(ccmSessionId, StatusReducer::interrupted);
    }

    /** Events of sessions this app did not register are ignored. */
    @EventListener
    public void on(HookEvent event) {
        update(event.ccmSessionId(), state -> StatusReducer.reduce(state, event));
    }

    public List<SessionSnapshot> sessions() {
        return states.values().stream()
                .map(SessionState::toSnapshot)
                .sorted(Comparator.comparing(SessionSnapshot::startedAt))
                .toList();
    }

    public List<SessionSnapshot> sessions(long projectId) {
        return sessions().stream().filter(session -> session.projectId() == projectId).toList();
    }

    public Optional<SessionSnapshot> find(String ccmSessionId) {
        return Optional.ofNullable(states.get(ccmSessionId)).map(SessionState::toSnapshot);
    }

    /** Max urgency over the project's live sessions; OFFLINE when there are none. */
    public AgentStatus projectStatus(long projectId) {
        return sessions(projectId).stream()
                .map(SessionSnapshot::status)
                .max(Comparator.naturalOrder())
                .orElse(AgentStatus.OFFLINE);
    }

    /** @return unsubscribe handle; callback fires (any thread) after any session or status change */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    private void update(String ccmSessionId, UnaryOperator<SessionState> change) {
        if (states.computeIfPresent(ccmSessionId, (id, state) -> change.apply(state)) != null) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("Session listener failed", e);
            }
        }
    }
}
