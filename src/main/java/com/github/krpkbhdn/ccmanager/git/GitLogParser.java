package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitLog.Commit;
import com.github.krpkbhdn.ccmanager.git.GitLog.Ref;
import com.github.krpkbhdn.ccmanager.git.GitLog.RefKind;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses the machine format of {@link #FORMAT}: records start with 0x1e, fields are separated by 0x1f. */
final class GitLogParser {

    static final String FORMAT = "--format=%x1e%H%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%D%x1f%s%x1f%b";
    static final int MAX_BODY_CHARS = 2000;
    private static final int SHORT_HASH_LENGTH = 7;
    private static final int FIELD_COUNT = 8;
    private static final String RECORD_SEPARATOR = "\u001e";
    private static final Pattern FIELD_SEPARATOR = Pattern.compile("\u001f", Pattern.LITERAL);
    private static final String HEADS = "refs/heads/";
    private static final String REMOTES = "refs/remotes/";
    private static final String TAGS = "refs/tags/";
    private static final String HEAD_POINTER = "HEAD -> ";
    private static final String TAG_PREFIX = "tag: ";

    private GitLogParser() {}

    static List<Commit> parse(String output) {
        return parse(output, MAX_BODY_CHARS);
    }

    static List<Commit> parse(String output, int maxBodyChars) {
        return Arrays.stream(output.split(RECORD_SEPARATOR))
                .filter(record -> !record.isBlank())
                .map(record -> parseRecord(record, maxBodyChars))
                .toList();
    }

    private static Commit parseRecord(String record, int maxBodyChars) {
        var fields = FIELD_SEPARATOR.split(record, FIELD_COUNT);
        var hash = fields[0];
        var parents = fields[1].isBlank() ? List.<String>of() : List.of(fields[1].split(" "));
        var body = fields.length == FIELD_COUNT ? truncate(fields[7].strip(), maxBodyChars) : "";
        return new Commit(hash, hash.substring(0, Math.min(SHORT_HASH_LENGTH, hash.length())), parents, fields[2], fields[3],
                OffsetDateTime.parse(fields[4]).toInstant(), fields[6], body, parseRefs(fields[5]));
    }

    private static String truncate(String body, int maxBodyChars) {
        return body.length() <= maxBodyChars ? body : body.substring(0, maxBodyChars);
    }

    static List<Ref> parseRefs(String decoration) {
        var refs = new ArrayList<Ref>();
        for (var part : decoration.split(", ")) {
            var name = part.strip();
            if (name.startsWith(HEAD_POINTER)) {
                refs.add(new Ref("HEAD", RefKind.HEAD));
                name = name.substring(HEAD_POINTER.length());
            }
            toRef(name).ifPresent(refs::add);
        }
        return List.copyOf(refs);
    }

    private static Optional<Ref> toRef(String name) {
        if (name.equals("HEAD")) {
            return Optional.of(new Ref(name, RefKind.HEAD));
        }
        if (name.startsWith(TAG_PREFIX + TAGS)) {
            return Optional.of(new Ref(name.substring(TAG_PREFIX.length() + TAGS.length()), RefKind.TAG));
        }
        if (name.startsWith(HEADS)) {
            return Optional.of(new Ref(name.substring(HEADS.length()), RefKind.LOCAL_BRANCH));
        }
        if (name.startsWith(REMOTES) && !name.endsWith("/HEAD")) {
            return Optional.of(new Ref(name.substring(REMOTES.length()), RefKind.REMOTE_BRANCH));
        }
        return Optional.empty();
    }
}
