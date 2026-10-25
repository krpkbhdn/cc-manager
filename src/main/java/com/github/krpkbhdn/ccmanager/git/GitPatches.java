package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import com.github.krpkbhdn.ccmanager.workspace.UnifiedDiffParser;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds unified patches for `git apply --cached` from a parsed {@link FileDiff}: one whole hunk, or only chosen +/- lines of a hunk.
 * Hunk indexes count the HUNK lines of {@code diff.lines()}; line indexes are positions in {@code diff.lines()} itself
 * (so the HUNK line of the first hunk is index 0), not line numbers in the file.
 * The diff must come from a parse that keeps carriage returns, otherwise CRLF files will not match.
 * A renamed file is addressed by its new path on both sides, which is what the index holds while the rename is staged.
 */
public final class GitPatches {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");
    private static final String NULL_DEVICE = "/dev/null";
    private static final String DEFAULT_FILE_MODE = "100644";

    private GitPatches() {}

    /** The whole hunk, to stage it, or (applied in reverse) to unstage it. */
    public static String hunk(FileDiff diff, int hunkIndex) {
        var range = hunkRange(diff, hunkIndex);
        var patch = new StringBuilder(header(diff, true));
        diff.lines().subList(range.start(), range.end()).forEach(line -> patch.append(raw(line)).append('\n'));
        return patch.toString();
    }

    /**
     * Patch for staging only the selected ADDED/REMOVED lines of the hunk: unselected additions are dropped, unselected
     * removals stay as context. Additions land right after the last selected removal of their change block.
     */
    public static String lines(FileDiff diff, int hunkIndex, Set<Integer> selectedLineIndexes) {
        return partial(diff, hunkIndex, new HunkSelection(selectedLineIndexes, false));
    }

    /**
     * Patch for `git apply --cached --reverse` that unstages only the selected lines of a staged hunk: unselected
     * additions stay as context, unselected removals are dropped.
     */
    public static String linesForUnstaging(FileDiff diff, int hunkIndex, Set<Integer> selectedLineIndexes) {
        return partial(diff, hunkIndex, new HunkSelection(selectedLineIndexes, true));
    }

    private record Range(int start, int end) {}

    private static String partial(FileDiff diff, int hunkIndex, HunkSelection selection) {
        var range = hunkRange(diff, hunkIndex);
        var body = selection.render(diff.lines(), range.start() + 1, range.end());
        if (!selection.touchedChange()) {
            throw new IllegalArgumentException("No added or removed line selected");
        }
        var wholeFile = body.stream().noneMatch(line -> line.startsWith(" "));
        return header(diff, wholeFile) + hunkHeader(diff.lines().get(range.start()).text(), body) + String.join("\n", body) + "\n";
    }

    private static Range hunkRange(FileDiff diff, int hunkIndex) {
        if (diff.binary()) {
            throw new IllegalArgumentException("Binary files cannot be staged by hunk");
        }
        var lines = diff.lines();
        if (lines.stream().anyMatch(line -> UnifiedDiffParser.TRUNCATED_MARKER.equals(line.text()))) {
            throw new IllegalArgumentException("The diff was truncated, so hunks cannot be staged");
        }
        var seen = -1;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).kind() == LineKind.HUNK && ++seen == hunkIndex) {
                return new Range(i, nextHunkStart(lines, i + 1));
            }
        }
        throw new IllegalArgumentException("No hunk with index " + hunkIndex);
    }

    private static int nextHunkStart(List<Line> lines, int from) {
        for (var i = from; i < lines.size(); i++) {
            if (lines.get(i).kind() == LineKind.HUNK) {
                return i;
            }
        }
        return lines.size();
    }

    /** @param wholeFile whether the patch creates/deletes the file (false once context lines tie it to existing content) */
    private static String header(FileDiff diff, boolean wholeFile) {
        var path = diff.path();
        var added = wholeFile && (diff.change() == ChangeType.ADDED || diff.change() == ChangeType.UNTRACKED);
        var deleted = wholeFile && diff.change() == ChangeType.DELETED;
        var header = new StringBuilder("diff --git a/").append(path).append(" b/").append(path).append('\n');
        if (added) {
            header.append("new file mode ").append(fileMode(diff)).append('\n');
        }
        if (deleted) {
            header.append("deleted file mode ").append(fileMode(diff)).append('\n');
        }
        header.append("--- ").append(added ? NULL_DEVICE : "a/" + path + pathSuffix(path)).append('\n');
        header.append("+++ ").append(deleted ? NULL_DEVICE : "b/" + path + pathSuffix(path)).append('\n');
        return header.toString();
    }

    private static String fileMode(FileDiff diff) {
        return diff.fileMode() == null ? DEFAULT_FILE_MODE : diff.fileMode();
    }

    private static String pathSuffix(String path) {
        return path.contains(" ") ? "\t" : "";
    }

    static String raw(Line line) {
        return switch (line.kind()) {
            case ADDED -> "+" + line.text();
            case REMOVED -> "-" + line.text();
            case CONTEXT -> " " + line.text();
            default -> line.text();
        };
    }

    private static String hunkHeader(String original, List<String> body) {
        var matcher = HUNK_HEADER.matcher(original);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a hunk header: " + original);
        }
        var oldCount = (int) body.stream().filter(line -> line.startsWith(" ") || line.startsWith("-")).count();
        var newCount = (int) body.stream().filter(line -> line.startsWith(" ") || line.startsWith("+")).count();
        var oldStart = Integer.parseInt(matcher.group(1));
        var newStart = Integer.parseInt(matcher.group(3));
        var originalNewCount = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
        var start = newCount == 0 ? Math.max(0, newStart - 1) : originalNewCount == 0 ? newStart + 1 : newStart;
        return "@@ -" + oldStart + "," + oldCount + " +" + start + "," + newCount + " @@" + matcher.group(5) + "\n";
    }
}
