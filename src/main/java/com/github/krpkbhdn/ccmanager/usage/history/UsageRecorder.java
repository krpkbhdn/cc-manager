package com.github.krpkbhdn.ccmanager.usage.history;

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.usage.RateLimitService;
import com.github.krpkbhdn.ccmanager.usage.SessionUsage;
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService;
import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stores a usage sample when a session finishes a turn or leaves the registry, and a rate limit sample on every change.
 * Keeps its own copy of session state because the usage service forgets a session as soon as it ends.
 */
@Component
public class UsageRecorder {

    static final int MAX_QUEUED_WRITES = 1000;
    private static final Duration DROP_WARNING_INTERVAL = Duration.ofMinutes(1);
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    private record Figures(Double cost, Double context) {
    }

    private final SessionUsageService usage;
    private final RateLimitService rateLimits;
    private final SessionRegistry registry;
    private final UsageHistoryRepository repository;
    private final UsageAlertService alerts;
    private final Duration rateLimitInterval;
    private final Executor writer;
    private RateLimitSample lastRateLimit;
    private final Map<String, SessionSnapshot> sessions = new HashMap<>();
    private final Map<String, SessionUsage> lastUsage = new HashMap<>();
    private final Map<String, Figures> lastWritten = new HashMap<>();
    private final Map<String, Double> lastCost = new ConcurrentHashMap<>();

    @Autowired
    public UsageRecorder(SessionRegistry registry, SessionUsageService usage, RateLimitService rateLimits,
            UsageHistoryRepository repository, UsageAlertService alerts, UsageProperties properties) {
        this(registry, usage, rateLimits, repository, alerts, properties, boundedWriter(MAX_QUEUED_WRITES));
    }

    /** Drops the newest write when full so a stalled database cannot grow memory without limit. */
    static ThreadPoolExecutor boundedWriter(int capacity) {
        var lastWarning = new AtomicLong();
        RejectedExecutionHandler drop = (task, executor) -> {
            var now = System.nanoTime();
            var last = lastWarning.get();
            if ((last == 0 || now - last >= DROP_WARNING_INTERVAL.toNanos()) && lastWarning.compareAndSet(last, now)) {
                log.warn("Usage write queue is full, dropping samples");
            }
        };
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            var thread = new Thread(task, "usage-recorder");
            thread.setDaemon(true);
            return thread;
        }, drop);
    }

    UsageRecorder(SessionRegistry registry, SessionUsageService usage, RateLimitService rateLimits,
            UsageHistoryRepository repository, UsageAlertService alerts, UsageProperties properties, Executor writer) {
        this.rateLimitInterval = properties.rateLimitSampleInterval();
        this.registry = registry;
        this.usage = usage;
        this.rateLimits = rateLimits;
        this.repository = repository;
        this.alerts = alerts;
        this.writer = writer;
        registry.subscribe(this::onSessionsChanged);
        usage.subscribe(this::onUsageChanged);
        rateLimits.subscribe(this::onRateLimitChanged);
    }

    private synchronized void onUsageChanged() {
        usage.all().forEach(lastUsage::put);
    }

    private synchronized void onSessionsChanged() {
        var live = registry.sessions();
        for (var session : live) {
            usage.of(session.ccmSessionId()).ifPresent(u -> lastUsage.put(session.ccmSessionId(), u));
            var before = sessions.put(session.ccmSessionId(), session);
            if (before != null && before.status() != AgentStatus.IDLE && session.status() == AgentStatus.IDLE) {
                record(session);
            }
        }
        var liveIds = live.stream().map(SessionSnapshot::ccmSessionId).toList();
        for (var id : List.copyOf(sessions.keySet())) {
            if (!liveIds.contains(id)) {
                var ended = sessions.remove(id);
                record(ended);
                writer.execute(() -> forgetCost(ended));
                lastUsage.remove(id);
                lastWritten.remove(id);
            }
        }
    }

    private void record(SessionSnapshot session) {
        var u = lastUsage.get(session.ccmSessionId());
        if (u == null || u.costUsd() == null) {
            return;
        }
        var figures = new Figures(u.costUsd(), u.contextUsedPercent());
        if (figures.equals(lastWritten.put(session.ccmSessionId(), figures))) {
            return;
        }
        var model = u.modelName() != null ? u.modelName() : session.model();
        var sample = new UsageSample(Instant.now(), session.ccmSessionId(), session.claudeSessionId(),
                session.projectId(), model, u.effort(), u.contextUsedPercent(), u.costUsd(), 0);
        writer.execute(() -> write(sample));
    }

    private void forgetCost(SessionSnapshot ended) {
        lastCost.remove(ended.ccmSessionId());
        if (ended.claudeSessionId() != null) {
            lastCost.remove(ended.claudeSessionId());
        }
    }

    @PreDestroy
    void shutdown() {
        if (writer instanceof ExecutorService service) {
            service.shutdown();
            try {
                if (!service.awaitTermination(SHUTDOWN_WAIT.toSeconds(), TimeUnit.SECONDS)) {
                    service.shutdownNow();
                }
            } catch (InterruptedException e) {
                service.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    void write(UsageSample sample) {
        try {
            var key = sample.claudeSessionId() != null ? sample.claudeSessionId() : sample.ccmSessionId();
            var known = lastCost.containsKey(key) ? lastCost.get(key) : lastCost.get(sample.ccmSessionId());
            var previous = known != null ? OptionalDouble.of(known)
                    : sample.claudeSessionId() != null ? repository.lastCost(key) : OptionalDouble.empty();
            repository.insertSample(new UsageSample(sample.at(), sample.ccmSessionId(), sample.claudeSessionId(),
                    sample.projectId(), sample.model(), sample.effort(), sample.contextPercent(), sample.costUsd(),
                    CostDelta.of(previous, sample.costUsd())));
            lastCost.put(key, sample.costUsd());
            lastCost.put(sample.ccmSessionId(), sample.costUsd());
            alerts.evaluateDailyBudget();
        } catch (RuntimeException e) {
            log.warn("Could not store usage sample of session {}", sample.ccmSessionId(), e);
        }
    }

    private synchronized void onRateLimitChanged() {
        rateLimits.current().ifPresent(snapshot -> {
            var sample = new RateLimitSample(snapshot.receivedAt(),
                    snapshot.fiveHour() == null ? null : snapshot.fiveHour().usedPercent(),
                    snapshot.fiveHour() == null ? null : snapshot.fiveHour().resetsAt(),
                    snapshot.sevenDay() == null ? null : snapshot.sevenDay().usedPercent(),
                    snapshot.sevenDay() == null ? null : snapshot.sevenDay().resetsAt());
            if (worthStoring(sample)) {
                lastRateLimit = sample;
                writer.execute(() -> writeRateLimit(sample));
            }
        });
    }

    private boolean worthStoring(RateLimitSample sample) {
        return lastRateLimit == null
                || !Objects.equals(sample.fiveHourPercent(), lastRateLimit.fiveHourPercent())
                || !Objects.equals(sample.sevenDayPercent(), lastRateLimit.sevenDayPercent())
                || !sample.at().isBefore(lastRateLimit.at().plus(rateLimitInterval));
    }

    private void writeRateLimit(RateLimitSample sample) {
        try {
            repository.insertRateLimit(sample);
        } catch (RuntimeException e) {
            log.warn("Could not store rate limit sample", e);
        }
    }
}
