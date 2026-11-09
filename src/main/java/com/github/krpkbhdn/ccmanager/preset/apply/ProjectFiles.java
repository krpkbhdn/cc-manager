package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.preset.ProjectPathRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** File access inside a project folder. Relative paths come from user input, so every one is checked here again before it is touched. */
final class ProjectFiles {

    static final long MAX_READ_BYTES = 2L * 1024 * 1024;

    enum State { MISSING, IDENTICAL, DIFFERENT }

    private ProjectFiles() {}

    /**
     * @return the absolute path of {@code relative} inside the project
     * @throws IllegalArgumentException when it is empty, absolute, has a drive, leaves the project or reaches out through a link
     */
    static Path resolve(Path projectDir, String relative) {
        var slashed = relative == null ? "" : relative.strip().replace('\\', '/');
        if (slashed.isEmpty() || slashed.startsWith("/") || slashed.contains(":")) {
            throw new IllegalArgumentException("The path must be relative to the project: " + relative);
        }
        for (var segment : slashed.split("/")) {
            if (!segment.isEmpty() && !segment.equals(".") && !segment.equals("..")) {
                ProjectPathRules.requireSafeSegment(segment, relative);
            }
        }
        var root = projectDir.toAbsolutePath().normalize();
        var target = root.resolve(slashed).normalize();
        if (!target.startsWith(root) || slashed.startsWith("..") || slashed.contains("/../")) {
            throw new IllegalArgumentException("The path must stay inside the project: " + relative);
        }
        try {
            var existing = target;
            while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (!existing.toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalArgumentException("The path leaves the project through a link: " + relative);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("The path cannot be verified: " + relative);
        }
        return target;
    }

    /** @return the file text, or "" when it is missing; fails for anything but a regular file up to {@link #MAX_READ_BYTES} */
    static String read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_READ_BYTES) {
            throw new IOException(file.getFileName() + " is not a regular file or is larger than " + MAX_READ_BYTES + " bytes");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** Like {@link #replace} but fails when the file no longer holds {@code expected}, so an edit made since it was read is not lost. */
    static void replaceIfUnchanged(Path file, String expected, String content) throws IOException {
        if (!read(file).equals(expected)) {
            throw new IOException(file.getFileName() + " was changed since it was checked, nothing was written");
        }
        replace(file, content);
    }

    static State compare(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            return State.MISSING;
        }
        return Files.isRegularFile(file) && lf(read(file)).equals(lf(content)) ? State.IDENTICAL : State.DIFFERENT;
    }

    /** Creates the file and its folders; fails when it exists, so nothing is ever overwritten. */
    static void createNew(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /**
     * Rewrites a file the user may already have edited through a temp file and an atomic move. A link is followed, so the file it
     * points to is rewritten and the link stays; {@link #resolve} already refused links that leave the project.
     */
    static void replace(Path link, String content) throws IOException {
        var file = Files.exists(link) ? link.toRealPath() : link;
        Files.createDirectories(file.getParent());
        var temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String lineEnding(String text) {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    static String lf(String text) {
        return text.replace("\r\n", "\n");
    }
}
