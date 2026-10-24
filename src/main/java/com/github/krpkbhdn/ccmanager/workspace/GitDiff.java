package com.github.krpkbhdn.ccmanager.workspace;

import java.util.List;

/** Working-tree changes against HEAD (staged, unstaged, untracked). gitRepository=false means no git here. */
public record GitDiff(boolean gitRepository, List<FileDiff> files) {

    public enum ChangeType { ADDED, MODIFIED, DELETED, RENAMED, UNTRACKED }

    public enum LineKind { META, HUNK, ADDED, REMOVED, CONTEXT }

    public record Line(LineKind kind, String text, Integer oldNumber, Integer newNumber) {}

    public record FileDiff(String path, String oldPath, ChangeType change, int additions, int deletions, boolean binary, List<Line> lines) {}
}
