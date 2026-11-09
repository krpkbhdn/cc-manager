package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewCleanupTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(7);

    @TempDir
    Path root;

    private final ReviewService service = mock(ReviewService.class);
    private ReviewCleanup cleanup;

    @BeforeEach
    void setUp() {
        var properties = new ReviewProperties(RETENTION, Duration.ofHours(1), 10, 1024, 8192, 36_000_000L, 50);
        cleanup = new ReviewCleanup(service, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(service.rootDirectory()).thenReturn(root);
        when(service.knownIds()).thenReturn(Set.of(1L));
    }

    @Test
    void deletesRowsOlderThanRetention() {
        cleanup.sweep();

        verify(service).deleteOlderThan(NOW.minus(RETENTION));
    }

    @Test
    void deletesOldOrphanAndNonNumericFoldersButKeepsKnownOnes() throws IOException {
        var known = folder("1", NOW.minus(Duration.ofHours(1)));
        var orphan = folder("2", NOW.minus(Duration.ofHours(1)));
        var stray = folder("stray", NOW.minus(Duration.ofHours(1)));

        cleanup.sweep();

        assertThat(known).exists();
        assertThat(orphan).doesNotExist();
        assertThat(stray).doesNotExist();
    }

    @Test
    void skipsFreshOrphan() throws IOException {
        var fresh = folder("3", NOW.minusSeconds(5));

        cleanup.sweep();

        assertThat(fresh).exists();
    }

    @Test
    void missingRootDoesNotThrow() {
        when(service.rootDirectory()).thenReturn(root.resolve("absent"));

        assertThatCode(cleanup::sweep).doesNotThrowAnyException();
    }

    @Test
    void serviceFailureDoesNotEscape() {
        when(service.deleteOlderThan(NOW.minus(RETENTION))).thenThrow(new IllegalStateException("boom"));

        assertThatCode(cleanup::sweep).doesNotThrowAnyException();
    }

    private Path folder(String name, Instant modified) throws IOException {
        var folder = Files.createDirectory(root.resolve(name));
        Files.setLastModifiedTime(folder, FileTime.from(modified));
        return folder;
    }
}
