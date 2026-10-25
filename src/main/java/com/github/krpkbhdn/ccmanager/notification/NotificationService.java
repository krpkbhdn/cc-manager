package com.github.krpkbhdn.ccmanager.notification;

import com.github.krpkbhdn.ccmanager.claude.hook.ActivityFormatter;
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Turns session status transitions (WORKING to IDLE, NEEDS_ACTION, errors) into notifications. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int HISTORY_LIMIT = 200;
    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(2);

    private record Seen(AgentStatus status, AppNotification lastNotification) {}

    private final Map<String, Seen> seen = new HashMap<>();
    private final ArrayDeque<AppNotification> history = new ArrayDeque<>();
    private final List<Consumer<AppNotification>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public NotificationService(SessionRegistry registry) {
        registry.subscribe(() -> onSessionsChanged(registry));
    }

    /** Newest last, in-memory, capped. */
    public synchronized List<AppNotification> history() {
        return List.copyOf(history);
    }

    /** @return unsubscribe handle; callback runs on an arbitrary thread */
    public Runnable subscribe(Consumer<AppNotification> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void onSessionsChanged(SessionRegistry registry) {
        var created = new ArrayList<AppNotification>();
        synchronized (this) {
            var sessions = registry.sessions();
            seen.keySet().retainAll(sessions.stream().map(SessionSnapshot::ccmSessionId).collect(Collectors.toSet()));
            sessions.forEach(session -> observe(session).ifPresent(created::add));
        }
        created.forEach(this::deliver);
    }

    private Optional<AppNotification> observe(SessionSnapshot session) {
        var previous = seen.get(session.ccmSessionId());
        if (previous == null) {
            seen.put(session.ccmSessionId(), new Seen(session.status(), null));
            return Optional.empty();
        }
        var notification = previous.status() == session.status() ? Optional.<AppNotification>empty() : transition(previous.status(), session)
                .filter(candidate -> !duplicate(previous.lastNotification(), candidate));
        var last = notification.orElse(previous.lastNotification());
        seen.put(session.ccmSessionId(), new Seen(session.status(), last));
        notification.ifPresent(this::remember);
        return notification;
    }

    /** A running (even silent) subagent means the work may go on, so going idle by staleness alone is not "finished". */
    private Optional<AppNotification> transition(AgentStatus from, SessionSnapshot session) {
        if (session.status() == AgentStatus.NEEDS_ACTION) {
            return Optional.of(create(NotificationKind.NEEDS_ACTION, session, "Claude needs your action", session.activity()));
        }
        if (session.status() != AgentStatus.IDLE || from == AgentStatus.IDLE || session.subagents().stream().anyMatch(SubagentSnapshot::running)) {
            return Optional.empty();
        }
        if (session.error() != null) {
            return Optional.of(create(NotificationKind.ERROR, session, "Claude stopped with an error", session.error()));
        }
        var interrupted = ActivityFormatter.INTERRUPTED.equals(session.activity());
        return interrupted ? Optional.empty() : Optional.of(create(NotificationKind.FINISHED, session, "Claude finished", session.activity()));
    }

    private AppNotification create(NotificationKind kind, SessionSnapshot session, String title, String body) {
        return new AppNotification(nextId.getAndIncrement(), session.lastEventAt(), kind, session.projectId(),
                session.ccmSessionId(), title, body == null ? "" : body);
    }

    private static boolean duplicate(AppNotification previous, AppNotification candidate) {
        return previous != null
                && previous.kind() == candidate.kind()
                && Duration.between(previous.at(), candidate.at()).compareTo(DUPLICATE_WINDOW) < 0;
    }

    private void remember(AppNotification notification) {
        history.addLast(notification);
        while (history.size() > HISTORY_LIMIT) {
            history.removeFirst();
        }
    }

    private void deliver(AppNotification notification) {
        for (var listener : listeners) {
            try {
                listener.accept(notification);
            } catch (RuntimeException e) {
                log.warn("Notification listener failed", e);
            }
        }
    }
}
