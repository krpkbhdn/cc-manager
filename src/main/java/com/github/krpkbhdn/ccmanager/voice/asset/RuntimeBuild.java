package com.github.krpkbhdn.ccmanager.voice.asset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** whisper.cpp release archives with the ggml and whisper libraries loaded in-process, pinned to one release tag. */
public enum RuntimeBuild {
    WINDOWS_X64_CPU("windows", "amd64", Flavor.CPU, "whisper-bin-x64.zip",
            "49dcc16de826f20bd53d44f947a1ae49dfa81f86cad67a64d80820cb192d674a", 8194445L),
    WINDOWS_X64_CUDA12("windows", "amd64", Flavor.CUDA, "whisper-cublas-12.4.0-bin-x64.zip",
            "443110ddaad70d4290ab2e77179e31cf712035bbc4fad56bb4519a90c917b39c", 670611449L);

    public enum Flavor { CPU, CUDA }

    private static final String RELEASE_URL = "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.2/";
    private static final String LIBRARY_DIRECTORY = "Release";
    private static final List<String> CORE_LIBRARIES = List.of("ggml-base.dll", "ggml.dll", "whisper.dll");
    private static final List<String> CUDA_RUNTIME_LIBRARIES = List.of("cudart64_12.dll", "cublasLt64_12.dll", "cublas64_12.dll");
    private static final String CUDA_BACKEND = "ggml-cuda.dll";

    private final String os;
    private final String arch;
    private final Flavor flavor;
    private final VoiceAsset asset;

    RuntimeBuild(String os, String arch, Flavor flavor, String archive, String sha256, long sizeBytes) {
        this.os = os;
        this.arch = arch;
        this.flavor = flavor;
        this.asset = new VoiceAsset("whisper.cpp " + flavor, RELEASE_URL + archive, sha256, sizeBytes,
                "runtime/" + name().toLowerCase(Locale.ROOT), true);
    }

    public String os() {
        return os;
    }

    public String arch() {
        return arch;
    }

    public Flavor flavor() {
        return flavor;
    }

    public VoiceAsset asset() {
        return asset;
    }

    /** The directory of the unpacked archive that holds the libraries, given the install directory of {@link #asset()}. */
    public Path libraryDirectory(Path installed) {
        return installed.resolve(LIBRARY_DIRECTORY);
    }

    /**
     * The libraries to load by full path, dependencies first. The CUDA runtime is bundled and loaded here because
     * ggml-cuda.dll looks for it on PATH only; the backends themselves (ggml-cpu-*.dll, ggml-cuda.dll) are loaded by ggml.
     */
    public List<String> libraries() {
        return flavor == Flavor.CUDA
                ? Stream.concat(CUDA_RUNTIME_LIBRARIES.stream(), CORE_LIBRARIES.stream()).toList()
                : CORE_LIBRARIES;
    }

    /** Whether every library of this build is in {@code libraryDirectory}. */
    public boolean isCompleteIn(Path libraryDirectory) {
        return Stream.concat(libraries().stream(), flavor == Flavor.CUDA ? Stream.of(CUDA_BACKEND) : Stream.<String>empty())
                .allMatch(name -> Files.isRegularFile(libraryDirectory.resolve(name)));
    }

    public static List<RuntimeBuild> forCurrentPlatform() {
        String currentOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String currentArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(build -> currentOs.contains(build.os) && build.arch.equals(currentArch))
                .toList();
    }
}
