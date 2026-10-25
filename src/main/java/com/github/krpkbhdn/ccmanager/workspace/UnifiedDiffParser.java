package com.github.krpkbhdn.ccmanager.workspace;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Pure parser of `git diff` unified output into per-file records, capping per-file and total line counts. */
public final class UnifiedDiffParser {

    public static final String TRUNCATED_MARKER = "... truncated";
    public static final int MAX_LINES_PER_FILE = 2000;
    public static final int MAX_TOTAL_LINES = 20000;
    private static final String FILE_START = "diff --git ";
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");
    private static final Pattern LF_ONLY = Pattern.compile("\\n");

    private final int maxLinesPerFile;
    private final int maxTotalLines;
    private final List<FileDiff> files = new ArrayList<>();
    private int totalLines;
    private FileBuilder current;

    private UnifiedDiffParser(int maxLinesPerFile, int maxTotalLines) {
        this.maxLinesPerFile = maxLinesPerFile;
        this.maxTotalLines = maxTotalLines;
    }

    public static List<FileDiff> parse(String diff, int maxLinesPerFile, int maxTotalLines) {
        return parse(diff, maxLinesPerFile, maxTotalLines, false);
    }

    /** With {@code keepCarriageReturns} a CR before the line feed stays in the line text, so patches rebuilt from the lines still apply to CRLF files. */
    public static List<FileDiff> parse(String diff, int maxLinesPerFile, int maxTotalLines, boolean keepCarriageReturns) {
        var parser = new UnifiedDiffParser(maxLinesPerFile, maxTotalLines);
        (keepCarriageReturns ? LF_ONLY : LINE_BREAK).splitAsStream(diff).forEach(parser::accept);
        parser.finishFile();
        return List.copyOf(parser.files);
    }

    private void accept(String line) {
        if (line.startsWith(FILE_START)) {
            finishFile();
            current = new FileBuilder(pathFromHeader(line.substring(FILE_START.length())));
        } else if (current != null) {
            current.accept(line);
        }
    }

    private void finishFile() {
        if (current != null) {
            files.add(current.build());
            current = null;
        }
    }

    private static String pathFromHeader(String header) {
        if (header.indexOf('"') >= 0) {
            return quotedPathFromHeader(header);
        }
        var middle = (header.length() - 1) / 2;
        if (header.startsWith("a/") && header.startsWith(" b/", middle) && header.substring(2, middle).equals(header.substring(middle + 3))) {
            return header.substring(2, middle);
        }
        var separator = header.lastIndexOf(" b/");
        return separator >= 0 ? header.substring(separator + 3) : header;
    }

    /** The new-side path of a header where git quoted at least one side. */
    private static String quotedPathFromHeader(String header) {
        var newSide = header.startsWith("\"") ? header.substring(Math.min(GitQuotedPath.endOfQuoted(header, 0) + 1, header.length())) : header.substring(header.indexOf(" \"b/") + 1);
        var path = GitQuotedPath.unquote(newSide);
        return path.startsWith("b/") ? path.substring(2) : path;
    }

    /** Accumulates one file; header lines set its change type, hunk lines fill its body. */
    private final class FileBuilder {
        private String path;
        private String oldPath;
        private ChangeType change = ChangeType.MODIFIED;
        private String fileMode;
        private boolean binary;
        private boolean inHunk;
        private boolean truncated;
        private int additions;
        private int deletions;
        private int oldNumber;
        private int newNumber;
        private final List<Line> lines = new ArrayList<>();

        FileBuilder(String path) {
            this.path = path;
        }

        void accept(String line) {
            var hunk = HUNK_HEADER.matcher(line);
            if (hunk.matches()) {
                inHunk = true;
                oldNumber = Integer.parseInt(hunk.group(1));
                newNumber = Integer.parseInt(hunk.group(2));
                add(new Line(LineKind.HUNK, line, null, null));
            } else if (inHunk) {
                acceptHunkLine(line);
            } else {
                acceptHeaderLine(line);
            }
        }

        private void acceptHeaderLine(String line) {
            if (line.startsWith("new file mode")) {
                fileMode = line.substring("new file mode ".length()).strip();
                change = ChangeType.ADDED;
            } else if (line.startsWith("deleted file mode")) {
                fileMode = line.substring("deleted file mode ".length()).strip();
                change = ChangeType.DELETED;
            } else if (line.startsWith("rename from ")) {
                change = ChangeType.RENAMED;
                oldPath = GitQuotedPath.unquote(line.substring("rename from ".length()));
            } else if (line.startsWith("rename to ")) {
                path = GitQuotedPath.unquote(line.substring("rename to ".length()));
            } else if (line.startsWith("Binary files ") || line.startsWith("GIT binary patch")) {
                binary = true;
                add(new Line(LineKind.META, line, null, null));
            }
        }

        private void acceptHunkLine(String line) {
            if (line.startsWith("+")) {
                additions++;
                add(new Line(LineKind.ADDED, line.substring(1), null, newNumber++));
            } else if (line.startsWith("-")) {
                deletions++;
                add(new Line(LineKind.REMOVED, line.substring(1), oldNumber++, null));
            } else if (line.startsWith("\\")) {
                add(new Line(LineKind.META, line, null, null));
            } else {
                add(new Line(LineKind.CONTEXT, line.isEmpty() ? line : line.substring(1), oldNumber++, newNumber++));
            }
        }

        private void add(Line line) {
            if (lines.size() >= maxLinesPerFile || totalLines >= maxTotalLines) {
                markTruncated();
                return;
            }
            lines.add(line);
            totalLines++;
        }

        private void markTruncated() {
            if (!truncated) {
                truncated = true;
                lines.add(new Line(LineKind.META, TRUNCATED_MARKER, null, null));
            }
        }

        FileDiff build() {
            return new FileDiff(path, oldPath, change, additions, deletions, binary, List.copyOf(lines), fileMode);
        }
    }
}
