package com.github.krpkbhdn.ccmanager.notification;

import com.github.krpkbhdn.ccmanager.claude.event.ActivityFormatter;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Turns session status transitions (WORKING to IDLE, NEEDS_ACTION, errors) into notifications. */
@Service
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private record Seen(AgentStatus status, AppNotification lastNotification) {}

    private final Map<String, Seen> seen = new HashMap<>();
    private final ArrayDeque<AppNotification> history = new ArrayDeque<>();
    private final List<Consumer<AppNotification>> listeners = new CopyOnWriteArrayList<>();
    private final NotificationProperties properties;
    private final AtomicLong nextId = new AtomicLong(1);

    public NotificationService(SessionRegistry registry, NotificationProperties properties) {
        this.properties = properties;
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

    /** For alerts not tied to a status transition; account-wide ones use projectId 0 and no session. */
    public AppNotification post(NotificationKind kind, long projectId, String ccmSessionId, String title, String body) {
        AppNotification notification;
        synchronized (this) {
            notification = new AppNotification(nextId.getAndIncrement(), Instant.now(), kind, projectId, ccmSessionId, title, body == null ? "" : body);
            remember(notification);
        }
        deliver(notification);
        return notification;
    }

    private void onSessionsChanged(SessionRegistry registry) {
        var created = new ArrayList<AppNotification>();
        synchronized (this) {
            var sessions = registry.sessions().stream().filter(session -> session.projectId() != ClaudeLauncher.ASSISTANT_PROJECT_ID).toList();
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

    private boolean duplicate(AppNotification previous, AppNotification candidate) {
        return previous != null
                && previous.kind() == candidate.kind()
                && previous.body().equals(candidate.body())
                && Duration.between(previous.at(), candidate.at()).compareTo(properties.duplicateWindow()) < 0;
    }

    private void remember(AppNotification notification) {
        history.addLast(notification);
        while (history.size() > properties.historyLimit()) {
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
