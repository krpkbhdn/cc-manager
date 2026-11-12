package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebReviewCleanupTest {

    @TempDir
    Path root;

    private WebReviewCleanup cleanup(Path folder) {
        return new WebReviewCleanup(folder, () -> Set.of(1L), Duration.ofHours(1), Clock.offset(Clock.systemUTC(), Duration.ofHours(1)), instant -> { }, Duration.ofDays(30));
    }

    @Test
    void keepsFoldersYoungerThanTheGraceWindow() throws IOException {
        var young = Files.createDirectories(root.resolve("2/7"));

        new WebReviewCleanup(root, () -> Set.of(1L), Duration.ofHours(1), Clock.systemUTC(), instant -> { }, Duration.ofDays(30)).sweep();

        assertThat(young).exists();
    }

    @Test
    void deletesFoldersOfRemovedProjectsAndKeepsKnownAndNonNumericOnes() throws IOException {
        var known = Files.createDirectories(root.resolve("1/5"));
        var orphan = Files.createDirectories(root.resolve("2/7"));
        var stray = Files.createDirectory(root.resolve("notes"));
        Files.writeString(known.resolve("item-1.png"), "x");

        cleanup(root).sweep();

        assertThat(known).exists();
        assertThat(root.resolve("2")).doesNotExist();
        assertThat(orphan).doesNotExist();
        assertThat(stray).exists();
    }

    @Test
    void doesNotFollowSymlinkedFolderOutsideTheRoot() throws IOException {
        var outside = Files.createTempDirectory("ccm-outside");
        Files.writeString(outside.resolve("keep.txt"), "x");
        try {
            Files.createSymbolicLink(root.resolve("9"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            return;
        }

        cleanup(root).sweep();

        assertThat(outside.resolve("keep.txt")).exists();
    }

    @Test
    void missingRootDoesNotThrow() {
        assertThatCode(() -> cleanup(root.resolve("absent")).sweep()).doesNotThrowAnyException();
    }
}
