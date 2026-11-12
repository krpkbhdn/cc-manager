package com.github.krpkbhdn.ccmanager.review;

import com.github.krpkbhdn.ccmanager.workspace.ScheduledSweep;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Removes reviews older than the retention and screenshot folders that no review row owns any more.
 */
@Component
public class ReviewCleanup extends ScheduledSweep {

    private static final Logger log = LoggerFactory.getLogger(ReviewCleanup.class);
    /** Folders younger than this are never treated as orphans: a writer may not have inserted its row yet. */
    public static final Duration ORPHAN_GRACE = Duration.ofMinutes(1);
    /** A capture file older than this was left by a crash: a capture is written and deleted within seconds. */
    public static final Duration STALE_CAPTURE_AGE = Duration.ofHours(1);

    private final ReviewService service;
    private final ReviewProperties properties;
    private final Clock clock;

    @Autowired
    public ReviewCleanup(ReviewService service, ReviewProperties properties) {
        this(service, properties, Clock.systemUTC());
    }

    ReviewCleanup(ReviewService service, ReviewProperties properties, Clock clock) {
        super("review-cleanup", properties.cleanupInterval());
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void sweep() {
        try {
            service.deleteOlderThan(clock.instant().minus(properties.retention()));
            service.deleteStaleCaptures(clock.instant().minus(STALE_CAPTURE_AGE));
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
            if (!isCaptureFolder(folder) && isOrphan(folder, known) && isOlderThanGrace(folder)) {
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

    private static boolean isCaptureFolder(Path folder) {
        return folder.getFileName().toString().equals(ReviewService.CAPTURE_FOLDER);
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
