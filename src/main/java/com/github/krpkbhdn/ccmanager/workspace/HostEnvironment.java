package com.github.krpkbhdn.ccmanager.workspace;

import java.nio.file.Path;
import java.util.List;

/** The slice of the operating system that executable discovery depends on; replaceable in tests. */
public interface HostEnvironment {

    enum OperatingSystem { WINDOWS, MACOS, LINUX }

    OperatingSystem os();

    Path home();

    /** @return the environment variable value or null when unset */
    String env(String name);

    List<Path> pathDirectories();

    boolean isFile(Path path);

    boolean isDirectory(Path path);

    /** @return direct child directories of parent whose name starts with prefix, in no particular order */
    List<Path> childDirectoriesStartingWith(Path parent, String prefix);

    /** Claude Code's configuration directory: CLAUDE_CONFIG_DIR when set, else ~/.claude. */
    default Path claudeConfigDirectory() {
        var configured = env("CLAUDE_CONFIG_DIR");
        return configured == null || configured.isBlank() ? home().resolve(".claude") : Path.of(configured);
    }

    default boolean windows() {
        return os() == OperatingSystem.WINDOWS;
    }

    /** Every PATH directory joined with every file name, in PATH order (existence not checked). */
    default List<Path> pathCandidates(List<String> fileNames) {
        return pathDirectories().stream()
                .flatMap(dir -> fileNames.stream().map(dir::resolve))
                .toList();
    }
}
