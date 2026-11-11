package com.github.krpkbhdn.ccmanager.review;

import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Removes reviews older than the retention and screenshot folders that no review row owns any more.
 */
@Component
public class ReviewCleanup {

    private static final Logger log = LoggerFactory.getLogger(ReviewCleanup.class);
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);
    private static final Duration ORPHAN_GRACE = Duration.ofMinutes(1);

    private final ReviewService service;
    private final ReviewProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofVirtual().name("review-cleanup").unstarted(runnable));

    @Autowired
    public ReviewCleanup(ReviewService service, ReviewProperties properties) {
        this(service, properties, Clock.systemUTC());
    }

    ReviewCleanup(ReviewService service, ReviewProperties properties, Clock clock) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::sweep, 0, properties.cleanupInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() throws InterruptedException {
        scheduler.shutdownNow();
        scheduler.awaitTermination(SHUTDOWN_WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Nothing escapes, because a scheduled task that throws is never run again. */
    public void sweep() {
        try {
            service.deleteOlderThan(clock.instant().minus(properties.retention()));
            deleteOrphanFolders();
        } catch (RuntimeException | Error e) {
            log.error("Review cleanup failed: {}", e.getClass().getSimpleName());
        }
    }

    private void deleteOrphanFolders() {
        var root = service.rootDirectory();
        if (!Files.isDirectory(root)) {
            return;
        }
        Set<Long> known = service.knownIds();
        for (var folder : subFolders(root)) {
            if (isOrphan(folder, known) && isOlderThanGrace(folder)) {
                deleteQuietly(folder);
            }
        }
    }

    private List<Path> subFolders(Path root) {
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.debug("Could not list review folders: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static boolean isOrphan(Path folder, Set<Long> known) {
        try {
            return !known.contains(Long.parseLong(folder.getFileName().toString()));
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private boolean isOlderThanGrace(Path folder) {
        try {
            var modified = Files.getLastModifiedTime(folder).toInstant();
            return !modified.isAfter(clock.instant().minus(ORPHAN_GRACE));
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteQuietly(Path folder) {
        try {
            TreeDeleter.delete(folder);
        } catch (IOException e) {
            log.debug("Could not delete an orphan review folder: {}", e.getClass().getSimpleName());
        }
    }
}
