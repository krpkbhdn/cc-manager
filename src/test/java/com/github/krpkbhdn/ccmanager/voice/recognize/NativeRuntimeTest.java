package com.github.krpkbhdn.ccmanager.voice.recognize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeRuntimeTest {

    @TempDir
    Path emptyDirectory;

    @TempDir
    Path brokenDirectory;

    @TempDir
    Path otherDirectory;

    @AfterEach
    void forgetClaim() {
        NativeRuntime.forgetFailedLoad();
    }

    @Test
    void missingLibraryIsReportedByPathInsteadOfCrashing() {
        assertThatThrownBy(() -> NativeRuntime.load(emptyDirectory, List.of("ggml-base.dll")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ggml-base.dll");
        assertThat(NativeRuntime.loaded()).isEmpty();
    }

    @Test
    void afterAFailedLoadOnlyTheSameDirectoryIsAccepted() throws IOException {
        Files.writeString(brokenDirectory.resolve("ggml-base.dll"), "not a library");
        Files.writeString(otherDirectory.resolve("ggml-base.dll"), "not a library");

        assertThatThrownBy(() -> NativeRuntime.load(brokenDirectory, List.of("ggml-base.dll")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot load");
        assertThatThrownBy(() -> NativeRuntime.load(otherDirectory, List.of("ggml-base.dll")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already loaded");
        assertThatThrownBy(() -> NativeRuntime.load(brokenDirectory, List.of("ggml-base.dll")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot load");
    }
}
