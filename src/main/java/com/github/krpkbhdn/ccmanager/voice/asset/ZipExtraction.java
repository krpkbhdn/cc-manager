package com.github.krpkbhdn.ccmanager.voice.asset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipFile;

/** Unpacks a zip beside its target as {@code .part} and moves it into place only when every entry stayed inside it. */
final class ZipExtraction {

    private static final int MOVE_ATTEMPTS = 5;
    private static final long MOVE_RETRY_PAUSE_MILLIS = 300;

    private ZipExtraction() {}

    /** Replaces an existing {@code directory}; a rejected or failed archive leaves it untouched and removes the partial one. */
    static void extract(Path zip, Path directory) throws IOException {
        var partial = directory.resolveSibling(directory.getFileName() + ".part");
        deleteRecursively(partial);
        try (var archive = new ZipFile(zip.toFile())) {
            Files.createDirectories(partial);
            var root = partial.toAbsolutePath().normalize();
            for (var entry : archive.stream().toList()) {
                var target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Archive entry escapes the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var in = archive.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            deleteRecursively(directory);
            moveIntoPlace(partial, directory);
        } finally {
            deleteRecursively(partial);
        }
    }

    /** Retries because on Windows a virus scanner or indexer may briefly hold the freshly written files. */
    static void moveIntoPlace(Path from, Path to) throws IOException {
        for (var attempt = 1; ; attempt++) {
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                if (attempt == MOVE_ATTEMPTS) {
                    throw new IOException("Could not move " + from.getFileName() + " into place after " + MOVE_ATTEMPTS
                            + " attempts; another program may be using its files", e);
                }
                pause();
            }
        }
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep(MOVE_RETRY_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var tree = Files.walk(path)) {
            for (var entry : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }
}
