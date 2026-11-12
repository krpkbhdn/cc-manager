package com.github.krpkbhdn.ccmanager.workspace;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A cleanup that runs once at start and then at a fixed delay on its own virtual thread; {@link #sweep} must never let an exception escape. */
public abstract class ScheduledSweep {

    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    private final Duration interval;
    private final ScheduledExecutorService scheduler;

    protected ScheduledSweep(String threadName, Duration interval) {
        this.interval = interval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofVirtual().name(threadName).unstarted(runnable));
    }

    @PostConstruct
    protected void start() {
        scheduler.scheduleWithFixedDelay(this::sweep, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    protected void stop() throws InterruptedException {
        scheduler.shutdownNow();
        scheduler.awaitTermination(SHUTDOWN_WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Nothing may escape, because a scheduled task that throws is never run again. */
    public abstract void sweep();
}
