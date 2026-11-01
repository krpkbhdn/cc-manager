package com.github.krpkbhdn.ccmanager.run;

import java.nio.file.Path;

/**
 * A named shell command of a project, run in {@code directory} (often a git worktree of the project).
 * {@code confirmed} is false for a command an agent wrote until the user has seen it and agreed to run it.
 */
public record RunConfiguration(long id, long projectId, String name, String command, Path directory, boolean confirmed) {

    public RunConfiguration(long id, long projectId, String name, String command, Path directory) {
        this(id, projectId, name, command, directory, true);
    }
}
