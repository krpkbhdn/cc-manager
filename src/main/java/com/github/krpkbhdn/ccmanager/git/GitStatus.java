package com.github.krpkbhdn.ccmanager.git;

import java.util.List;

/**
 * Snapshot of a repository's state (git status --porcelain=v2 --branch). {@code branch} is null when HEAD is detached,
 * {@code upstream} is null without a tracking branch. Paths are relative to the repository root, '/' separated.
 */
public record GitStatus(
        boolean repository,
        String branch,
        String head,
        boolean detached,
        String upstream,
        int ahead,
        int behind,
        Operation operation,
        int stashCount,
        String stashTop,
        List<Change> changes) {

    /** A merge/rebase/cherry-pick/revert that is stopped and waiting for the user. */
    public enum Operation { NONE, MERGE, REBASE, CHERRY_PICK, REVERT }

    public enum Kind { NONE, ADDED, MODIFIED, DELETED, RENAMED, COPIED, TYPE_CHANGED, UNTRACKED, UNMERGED }

    /** One path; {@code staged} is the index-vs-HEAD state, {@code unstaged} the worktree-vs-index state. */
    public record Change(String path, String oldPath, Kind staged, Kind unstaged) {

        public boolean conflicted() {
            return staged == Kind.UNMERGED || unstaged == Kind.UNMERGED;
        }

        public boolean hasStaged() {
            return staged != Kind.NONE && staged != Kind.UNMERGED;
        }

        public boolean hasUnstaged() {
            return unstaged != Kind.NONE && unstaged != Kind.UNMERGED;
        }
    }

    public static GitStatus notARepository() {
        return new GitStatus(false, null, null, false, null, 0, 0, Operation.NONE, 0, null, List.of());
    }
}
