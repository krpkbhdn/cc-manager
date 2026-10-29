package com.github.krpkbhdn.ccmanager.voice.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeBuildTest {

    @TempDir
    Path directory;

    @Test
    void libraryDirectoryIsTheReleaseFolderOfTheArchive() {
        assertThat(RuntimeBuild.WINDOWS_X64_CPU.libraryDirectory(directory)).isEqualTo(directory.resolve("Release"));
    }

    @Test
    void cpuBuildLoadsGgmlBeforeWhisper() {
        assertThat(RuntimeBuild.WINDOWS_X64_CPU.libraries()).containsExactly("ggml-base.dll", "ggml.dll", "whisper.dll");
    }

    @Test
    void cudaBuildLoadsItsBundledRuntimeFirst() {
        assertThat(RuntimeBuild.WINDOWS_X64_CUDA12.libraries()).containsExactly("cudart64_12.dll", "cublasLt64_12.dll",
                "cublas64_12.dll", "ggml-base.dll", "ggml.dll", "whisper.dll");
    }

    @Test
    void isCompleteOnlyWithEveryLibrary() throws IOException {
        for (var name : RuntimeBuild.WINDOWS_X64_CPU.libraries()) {
            assertThat(RuntimeBuild.WINDOWS_X64_CPU.isCompleteIn(directory)).isFalse();
            Files.createFile(directory.resolve(name));
        }

        assertThat(RuntimeBuild.WINDOWS_X64_CPU.isCompleteIn(directory)).isTrue();
        assertThat(RuntimeBuild.WINDOWS_X64_CUDA12.isCompleteIn(directory)).isFalse();
    }

    @Test
    void cudaBuildAlsoNeedsItsBackend() throws IOException {
        for (var name : RuntimeBuild.WINDOWS_X64_CUDA12.libraries()) {
            Files.createFile(directory.resolve(name));
        }
        assertThat(RuntimeBuild.WINDOWS_X64_CUDA12.isCompleteIn(directory)).isFalse();

        Files.createFile(directory.resolve("ggml-cuda.dll"));

        assertThat(RuntimeBuild.WINDOWS_X64_CUDA12.isCompleteIn(directory)).isTrue();
    }
}
