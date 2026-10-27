package com.github.krpkbhdn.ccmanager.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses {@code git worktree list --porcelain}: blocks separated by a blank line, one "key [value]" per line. */
final class WorktreePorcelainParser {

    private static final String BRANCH_PREFIX = "refs/heads/";

    record ParsedWorktree(Path path, String head, String branch, boolean detached, boolean bare, boolean locked, boolean prunable) {}

    private WorktreePorcelainParser() {}

    static List<ParsedWorktree> parse(String porcelainOutput) {
        var result = new ArrayList<ParsedWorktree>();
        for (var block : porcelainOutput.strip().replace("\r\n", "\n").split("\n\\s*\n")) {
            if (!block.isBlank()) {
                result.add(parseBlock(block));
            }
        }
        return result;
    }

    private static ParsedWorktree parseBlock(String block) {
        Path path = null;
        var head = "";
        var branch = "";
        boolean detached = false;
        boolean bare = false;
        boolean locked = false;
        boolean prunable = false;
        for (var line : block.split("\n")) {
            var space = line.indexOf(' ');
            var key = space < 0 ? line : line.substring(0, space);
            var value = space < 0 ? "" : line.substring(space + 1);
            switch (key) {
                case "worktree" -> path = Path.of(value);
                case "HEAD" -> head = value;
                case "branch" -> branch = value.startsWith(BRANCH_PREFIX) ? value.substring(BRANCH_PREFIX.length()) : value;
                case "detached" -> detached = true;
                case "bare" -> bare = true;
                case "locked" -> locked = true;
                case "prunable" -> prunable = true;
                default -> { }
            }
        }
        return new ParsedWorktree(path, head, branch, detached, bare, locked, prunable);
    }
}
