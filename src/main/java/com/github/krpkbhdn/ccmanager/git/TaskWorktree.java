package com.github.krpkbhdn.ccmanager.git;

import java.nio.file.Path;

/** A task's git worktree of a project; {@code baseBranch} is empty when the project's HEAD was detached at creation. */
public record TaskWorktree(long id, long projectId, Path path, String branch, String baseBranch, String task) {}
