package com.github.krpkbhdn.ccmanager.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Pure checks for a new worktree's branch name (git check-ref-format rules) and target folder. */
public final class WorktreeNames {

    private static final String FORBIDDEN_CHARACTERS = "~^:?*[\\";
    private static final List<String> FORBIDDEN_PARTS = List.of("..", "@{", "//");
    private static final List<String> FORBIDDEN_ENDINGS = List.of("/", ".", ".lock");
    private static final List<String> FORBIDDEN_STARTS = List.of("-", "/");

    private static final String RESERVED_NAME = "@";
    private static final String COMPONENT_HIDDEN_START = ".";
    private static final String COMPONENT_LOCK_ENDING = ".lock";

    private WorktreeNames() {}

    public static Optional<String> branchProblem(String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("Branch name is empty");
        }
        var invalid = name.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c) || FORBIDDEN_CHARACTERS.indexOf(c) >= 0)
                || FORBIDDEN_PARTS.stream().anyMatch(name::contains)
                || FORBIDDEN_ENDINGS.stream().anyMatch(name::endsWith)
                || FORBIDDEN_STARTS.stream().anyMatch(name::startsWith)
                || RESERVED_NAME.equals(name)
                || Stream.of(name.split("/")).anyMatch(part -> part.startsWith(COMPONENT_HIDDEN_START) || part.endsWith(COMPONENT_LOCK_ENDING));
        return invalid ? Optional.of("'" + name + "' is not a valid branch name") : Optional.empty();
    }

    public static Optional<String> pathProblem(Path target) {
        if (target == null || target.toString().isBlank()) {
            return Optional.of("Folder is empty");
        }
        try {
            if (!target.isAbsolute()) {
                return Optional.of("Folder must be an absolute path");
            }
            var parent = target.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return Optional.of("Parent folder does not exist: " + parent);
            }
            return Files.exists(target) && !isEmptyDirectory(target) ? Optional.of("Folder already exists and is not empty: " + target) : Optional.empty();
        } catch (InvalidPathException | IOException e) {
            return Optional.of("Folder is not readable: " + target);
        }
    }

    private static boolean isEmptyDirectory(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(target)) {
            return entries.findAny().isEmpty();
        }
    }
}
