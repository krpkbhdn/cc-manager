package com.github.krpkbhdn.ccmanager.git;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the output of {@link #FORMAT} from `git for-each-ref` into {@link GitBranch}es, skipping remote HEAD symrefs. */
final class BranchListParser {

    static final String FORMAT = String.join("%1f", "%(refname)", "%(HEAD)", "%(upstream:short)", "%(upstream:track)",
            "%(committerdate:iso-strict)", "%(objectname:short)", "%(contents:subject)");

    private static final String LOCAL_PREFIX = "refs/heads/";
    private static final String REMOTE_PREFIX = "refs/remotes/";
    private static final String REMOTE_HEAD_SUFFIX = "/HEAD";
    private static final String FIELD_SEPARATOR = "\u001f";
    private static final int FIELD_COUNT = 7;
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d+)");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d+)");

    private BranchListParser() {}

    /** Current branch first, then locals by name, then remotes by name. */
    static List<GitBranch> parse(String output) {
        return output.lines()
                .map(BranchListParser::parseLine)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(GitBranch::remote).thenComparing(branch -> !branch.current()).thenComparing(GitBranch::name))
                .toList();
    }

    private static Optional<GitBranch> parseLine(String line) {
        var fields = line.split(FIELD_SEPARATOR, -1);
        if (fields.length < FIELD_COUNT) {
            return Optional.empty();
        }
        var refname = fields[0];
        if (refname.startsWith(LOCAL_PREFIX)) {
            return Optional.of(branch(refname.substring(LOCAL_PREFIX.length()), false, fields));
        }
        if (refname.startsWith(REMOTE_PREFIX) && !refname.endsWith(REMOTE_HEAD_SUFFIX)) {
            return Optional.of(branch(refname.substring(REMOTE_PREFIX.length()), true, fields));
        }
        return Optional.empty();
    }

    private static GitBranch branch(String name, boolean remote, String[] fields) {
        var upstream = fields[2].isEmpty() ? null : fields[2];
        return new GitBranch(name, remote, "*".equals(fields[1]), upstream, count(AHEAD, fields[3]), count(BEHIND, fields[3]),
                fields[5], fields[6], parseDate(fields[4]));
    }

    private static int count(Pattern pattern, String track) {
        Matcher matcher = pattern.matcher(track);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static Instant parseDate(String isoStrict) {
        try {
            return OffsetDateTime.parse(isoStrict).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
