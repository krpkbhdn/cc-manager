package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitStatus.Change;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parses `git status --porcelain=v2 --branch -z` into the branch header fields and the list of changes. */
final class PorcelainStatusParser {

    record Parsed(String branch, String head, boolean detached, String upstream, int ahead, int behind, List<Change> changes) {}

    private static final Pattern AHEAD_BEHIND = Pattern.compile("\\+(\\d+) -(\\d+)");
    private static final String INITIAL_COMMIT = "(initial)";
    private static final String DETACHED = "(detached)";
    private static final int ORDINARY_FIELDS = 9;
    private static final int RENAMED_FIELDS = 10;
    private static final int UNMERGED_FIELDS = 11;
    private static final char UNCHANGED = '.';

    private String branch;
    private String head;
    private boolean detached;
    private String upstream;
    private int ahead;
    private int behind;
    private final List<Change> changes = new ArrayList<>();

    private PorcelainStatusParser() {}

    static Parsed parse(String porcelain) {
        var parser = new PorcelainStatusParser();
        var records = porcelain.split("\0");
        for (var i = 0; i < records.length; i++) {
            var record = records[i];
            if (record.startsWith("2 ")) {
                i++;
                parser.renamed(record, i < records.length ? records[i] : null);
            } else {
                parser.accept(record);
            }
        }
        return new Parsed(parser.branch, parser.head, parser.detached, parser.upstream, parser.ahead, parser.behind,
                List.copyOf(parser.changes));
    }

    private void accept(String record) {
        if (record.startsWith("# ")) {
            header(record.substring(2));
        } else if (record.startsWith("1 ")) {
            var fields = record.split(" ", ORDINARY_FIELDS);
            changes.add(new Change(fields[ORDINARY_FIELDS - 1], null, kind(fields[1].charAt(0)), kind(fields[1].charAt(1))));
        } else if (record.startsWith("u ")) {
            var fields = record.split(" ", UNMERGED_FIELDS);
            changes.add(new Change(fields[UNMERGED_FIELDS - 1], null, Kind.UNMERGED, Kind.UNMERGED));
        } else if (record.startsWith("? ")) {
            changes.add(new Change(record.substring(2), null, Kind.NONE, Kind.UNTRACKED));
        }
    }

    private void renamed(String record, String originalPath) {
        var fields = record.split(" ", RENAMED_FIELDS);
        changes.add(new Change(fields[RENAMED_FIELDS - 1], originalPath, kind(fields[1].charAt(0)), kind(fields[1].charAt(1))));
    }

    private void header(String line) {
        var space = line.indexOf(' ');
        var key = line.substring(0, space);
        var value = line.substring(space + 1);
        switch (key) {
            case "branch.oid" -> head = INITIAL_COMMIT.equals(value) ? null : value;
            case "branch.head" -> {
                detached = DETACHED.equals(value);
                branch = detached ? null : value;
            }
            case "branch.upstream" -> upstream = value;
            case "branch.ab" -> aheadBehind(value);
            default -> { }
        }
    }

    private void aheadBehind(String value) {
        var matcher = AHEAD_BEHIND.matcher(value);
        if (matcher.matches()) {
            ahead = Integer.parseInt(matcher.group(1));
            behind = Integer.parseInt(matcher.group(2));
        }
    }

    private static Kind kind(char code) {
        return switch (code) {
            case UNCHANGED -> Kind.NONE;
            case 'M' -> Kind.MODIFIED;
            case 'T' -> Kind.TYPE_CHANGED;
            case 'A' -> Kind.ADDED;
            case 'D' -> Kind.DELETED;
            case 'R' -> Kind.RENAMED;
            case 'C' -> Kind.COPIED;
            default -> Kind.NONE;
        };
    }
}
