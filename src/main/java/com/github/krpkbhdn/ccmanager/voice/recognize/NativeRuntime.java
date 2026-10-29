package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The ggml and whisper libraries of one directory, loaded into this process once and never unloaded.
 * Windows resolves a library by file name, so a second directory would silently reuse the first one's
 * modules, and unloading them while ggml still holds backends it loaded itself leaves dangling pointers
 * (the old access violation). A process therefore gets exactly one runtime; switching builds needs a restart.
 */
public final class NativeRuntime {

    private static NativeRuntime loaded;
    private static Path claimed;

    private final Path directory;
    private final Linker linker = Linker.nativeLinker();
    private final List<SymbolLookup> libraries;
    private final List<GgmlGpu> gpus;

    /**
     * Loads {@code libraryNames} by full path in the given order (dependencies first), then the ggml backends of the directory.
     * The directory is claimed before the first library loads: after a failed load only a retry of the same directory is accepted.
     */
    public static synchronized NativeRuntime load(Path directory, List<String> libraryNames) {
        var dir = directory.toAbsolutePath().normalize();
        if (claimed != null && !claimed.equals(dir)) {
            throw new IllegalStateException("A whisper runtime is already loaded from " + claimed
                    + "; one process can load only one, restart the app to use " + dir);
        }
        libraryNames.forEach(name -> requireFile(dir.resolve(name)));
        claimed = dir;
        if (loaded == null) {
            loaded = new NativeRuntime(dir, libraryNames);
        }
        return loaded;
    }

    public static synchronized Optional<NativeRuntime> loaded() {
        return Optional.ofNullable(loaded);
    }

    /** Test seam: a real process never unloads, but a test of the failure path must not poison later tests. */
    static synchronized void forgetFailedLoad() {
        if (loaded == null) {
            claimed = null;
        }
    }

    private NativeRuntime(Path directory, List<String> libraryNames) {
        this.directory = directory;
        this.libraries = libraryNames.stream().map(name -> open(directory.resolve(name))).toList();
        loadBackends();
        this.gpus = GgmlDevices.gpus(this);
    }

    public Path directory() {
        return directory;
    }

    public List<GgmlGpu> gpus() {
        return gpus;
    }

    MethodHandle handle(String symbol, FunctionDescriptor descriptor) {
        var address = libraries.stream().map(library -> library.find(symbol)).flatMap(Optional::stream).findFirst()
                .orElseThrow(() -> new IllegalStateException("Symbol not found in the whisper runtime: " + symbol));
        return linker.downcallHandle(address, descriptor);
    }

    private static void requireFile(Path library) {
        if (!Files.isRegularFile(library)) {
            throw new IllegalStateException("Missing whisper runtime library: " + library);
        }
    }

    private static SymbolLookup open(Path library) {
        try {
            return SymbolLookup.libraryLookup(library, Arena.global());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + library + ": " + e.getMessage(), e);
        }
    }

    private void loadBackends() {
        try {
            handle("ggml_backend_load_all_from_path", FunctionDescriptor.ofVoid(ADDRESS))
                    .invoke(Arena.global().allocateFrom(directory.toString()));
        } catch (Throwable e) {
            throw NativeFailure.of("ggml_backend_load_all_from_path", e);
        }
    }
}
