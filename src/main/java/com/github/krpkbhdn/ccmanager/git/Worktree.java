package com.github.krpkbhdn.ccmanager.git;

import java.nio.file.Path;

/** One entry of {@code git worktree list}; {@code branch} is the short name, empty when detached. */
public record Worktree(Path path, String branch, String head, boolean main, boolean detached, boolean locked,
        boolean prunable, boolean dirty, boolean merged) {}
