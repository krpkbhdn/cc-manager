package com.github.krpkbhdn.ccmanager.run;

import java.nio.file.Path;

/** A named shell command of a project, run in {@code directory} (often a git worktree of the project). */
public record RunConfiguration(long id, long projectId, String name, String command, Path directory) {}
