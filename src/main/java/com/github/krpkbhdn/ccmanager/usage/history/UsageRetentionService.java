package com.github.krpkbhdn.ccmanager.usage.history;

import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Folds raw usage samples older than the retention into daily totals, at start and then periodically. */
@Service
public class UsageRetentionService {

    private static final Logger log = LoggerFactory.getLogger(UsageRetentionService.class);

    private final UsageHistoryRepository repository;
    private final UsageProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "usage-retention");
        thread.setDaemon(true);
        return thread;
    });

    public UsageRetentionService(UsageHistoryRepository repository, UsageProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(() -> runOnce(Instant.now()), 0,
                properties.retentionInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    void runOnce(Instant now) {
        try {
            repository.aggregateBefore(now.minus(properties.rawRetention()).toEpochMilli());
        } catch (RuntimeException e) {
            log.warn("Usage retention failed", e);
        }
    }
}
