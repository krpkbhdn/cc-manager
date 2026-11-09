package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FileApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private final FileApplier applier = new FileApplier();

    private static PresetComponent file(String path, String content, String... ignore) {
        var payload = JSON.createObjectNode().put("path", path).put("content", content);
        var lines = payload.putArray("gitignore");
        for (var line : ignore) {
            lines.add(line);
        }
        return new PresetComponent(1, PresetComponentKind.FILE, "f", payload);
    }

    @Test
    void missingFileIsPlannedAsCreateAndWrittenOnApply() throws Exception {
        var component = file("docs/a.txt", "hello");

        assertThat(applier.plan(component, dir).action()).isEqualTo(ChangeAction.CREATE);
        applier.apply(component, dir);

        assertThat(Files.readString(dir.resolve("docs/a.txt"))).isEqualTo("hello");
    }

    @Test
    void existingDifferentFileIsAConflictAndStaysUnchanged() throws Exception {
        Files.writeString(dir.resolve("a.txt"), "mine");

        assertThat(applier.plan(file("a.txt", "theirs"), dir).action()).isEqualTo(ChangeAction.CONFLICT);
        assertThat(Files.readString(dir.resolve("a.txt"))).isEqualTo("mine");
    }

    @Test
    void identicalFileIsPresentEvenWithOtherLineEndings() throws Exception {
        Files.writeString(dir.resolve("a.txt"), "x\r\ny\r\n");

        assertThat(applier.plan(file("a.txt", "x\ny\n"), dir).action()).isEqualTo(ChangeAction.PRESENT);
    }

    @Test
    void applyDoesNotOverwriteADifferentFile() throws Exception {
        Files.writeString(dir.resolve("a.txt"), "mine");

        try {
            applier.apply(file("a.txt", "theirs"), dir);
        } catch (Exception refusing) {
            // refusing is acceptable, overwriting is not
        }

        assertThat(Files.readString(dir.resolve("a.txt"))).isEqualTo("mine");
    }

    @Test
    void missingGitignoreLinesAreAppendedOnceKeepingExistingOnes() throws Exception {
        Files.writeString(dir.resolve(".gitignore"), "build/");
        var component = file("a.txt", "x", "build/", ".env", ".env");

        applier.apply(component, dir);

        assertThat(Files.readString(dir.resolve(".gitignore")).lines().toList()).containsExactly("build/", ".env");
    }

    @Test
    void identicalFileWithMissingIgnoreLinesIsAnUpdate() throws Exception {
        Files.writeString(dir.resolve("a.txt"), "x");

        assertThat(applier.plan(file("a.txt", "x", ".env"), dir).action()).isEqualTo(ChangeAction.UPDATE);
    }

    @Test
    void unsafePathsAreRejectedAndNothingIsWritten() throws Exception {
        var project = Files.createDirectory(dir.resolve("project"));
        for (var bad : new String[] {"../out.txt", "a/../../out.txt", "/etc/x", "C:/x.txt", "C:\\x.txt", ".git/config", "sub/.git/hooks/x", ""}) {
            var component = file(bad, "x");
            assertThatThrownBy(() -> applier.plan(component, project)).as(bad).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> applier.apply(component, project)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(dir.resolve("out.txt")).doesNotExist();
    }

    @Test
    void pathThroughALinkLeavingTheProjectIsRejected(@TempDir Path outside) throws Exception {
        try {
            Files.createSymbolicLink(dir.resolve("link"), outside);
        } catch (Exception e) {
            Assumptions.abort("symlinks not available");
        }

        assertThatThrownBy(() -> applier.apply(file("link/x.txt", "x"), dir)).isInstanceOf(IllegalArgumentException.class);
        assertThat(outside.resolve("x.txt")).doesNotExist();
    }
}
