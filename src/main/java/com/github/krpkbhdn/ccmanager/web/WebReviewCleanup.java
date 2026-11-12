package com.github.krpkbhdn.ccmanager.web;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.review.ReviewCleanup;
import com.github.krpkbhdn.ccmanager.review.ReviewProperties;
import com.github.krpkbhdn.ccmanager.workspace.ScheduledSweep;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Prunes APPROVED revisions older than the retention (PENDING and SENT ones are never touched) and deletes screenshot folders under
 * {@code <dataDir>/web-review} whose numeric name is no longer a project (rows cascade, files do not).
 */
@Component
public class WebReviewCleanup extends ScheduledSweep {

    private static final Logger log = LoggerFactory.getLogger(WebReviewCleanup.class);

    private final Path root;
    private final Supplier<Set<Long>> projectIds;
    private final Clock clock;
    private final Consumer<Instant> approvedPruner;
    private final Duration retention;

    @Autowired
    public WebReviewCleanup(JdbcClient jdbc, AppProperties app, ReviewProperties reviewProperties, WebReviewService service,
                            WebReviewProperties properties) {
        this(app.dataDir().resolve("web-review"), () -> new HashSet<>(jdbc.sql("SELECT id FROM project").query(Long.class).list()),
                reviewProperties.cleanupInterval(), Clock.systemUTC(), service::deleteApprovedBefore, properties.retention());
    }

    WebReviewCleanup(Path root, Supplier<Set<Long>> projectIds, Duration interval, Clock clock, Consumer<Instant> approvedPruner,
                     Duration retention) {
        super("web-review-cleanup", interval);
        this.root = root;
        this.projectIds = projectIds;
        this.clock = clock;
        this.approvedPruner = approvedPruner;
        this.retention = retention;
    }

    @Override
    public void sweep() {
        try {
            approvedPruner.accept(clock.instant().minus(retention));
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            Set<Long> known = projectIds.get();
            for (var folder : realFolders()) {
                if (isRemovedProject(folder, known) && isOlderThanGrace(folder)) {
                    deleteQuietly(folder);
                }
            }
        } catch (RuntimeException | Error e) {
            log.error("Web review cleanup failed: {}", e.getClass().getSimpleName());
        }
    }

    private List<Path> realFolders() {
        var folders = new ArrayList<Path>();
        try (var children = Files.newDirectoryStream(root, child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))) {
            children.forEach(folders::add);
        } catch (IOException e) {
            log.debug("Could not list web review folders: {}", e.getClass().getSimpleName());
        }
        return folders;
    }

    private static boolean isRemovedProject(Path folder, Set<Long> known) {
        try {
            return !known.contains(Long.parseLong(folder.getFileName().toString()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isOlderThanGrace(Path folder) {
        try {
            return !Files.getLastModifiedTime(folder).toInstant().isAfter(clock.instant().minus(ReviewCleanup.ORPHAN_GRACE));
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteQuietly(Path folder) {
        try {
            TreeDeleter.delete(folder);
        } catch (IOException e) {
            log.debug("Could not delete an orphan web review folder: {}", e.getClass().getSimpleName());
        }
    }
}
