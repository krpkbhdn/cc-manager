package com.github.krpkbhdn.ccmanager.voice.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipExtractionTest {

    @TempDir
    Path dir;

    @Test
    void replacesExistingDirectory() throws IOException {
        var target = dir.resolve("runtime");
        Files.createDirectories(target);
        Files.writeString(target.resolve("old.txt"), "old");

        ZipExtraction.extract(zip("new.txt"), target);

        assertThat(target.resolve("new.txt")).hasContent("x");
        assertThat(target.resolve("old.txt")).doesNotExist();
        assertThat(dir.resolve("runtime.part")).doesNotExist();
    }

    @Test
    void rejectedArchiveLeavesExistingDirectoryUntouched() throws IOException {
        var target = dir.resolve("runtime");
        Files.createDirectories(target);
        Files.writeString(target.resolve("old.txt"), "old");

        assertThatThrownBy(() -> ZipExtraction.extract(zip("../evil.txt"), target)).isInstanceOf(IOException.class);

        assertThat(target.resolve("old.txt")).hasContent("old");
        assertThat(dir.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void moveFailureIsReportedAfterRetries() {
        assertThatThrownBy(() -> ZipExtraction.moveIntoPlace(dir.resolve("missing"), dir.resolve("target"), 1))
                .isInstanceOf(IOException.class).hasMessageContaining("5 attempts");
    }

    private Path zip(String entryName) throws IOException {
        var zip = Files.createTempFile(dir, "a", ".zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write('x');
            out.closeEntry();
        }
        return zip;
    }
}
