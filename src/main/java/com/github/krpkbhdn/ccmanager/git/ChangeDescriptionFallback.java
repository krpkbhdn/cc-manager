package com.github.krpkbhdn.ccmanager.git;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The description built without a model: task, then branch name, then newest commit subject. */
final class ChangeDescriptionFallback {

    static final String GENERIC_TITLE = "Update";
    private static final int MAX_LISTED_FILES = 30;
    private static final Set<String> DEFAULT_BRANCHES = Set.of("develop", "main", "master", "HEAD");

    private ChangeDescriptionFallback() {}

    /** @param subjects newest first */
    static ChangeDescription of(String task, String branch, List<String> subjects, List<String> files) {
        var title = fromText(task.lines().filter(line -> !line.isBlank()).findFirst().orElse(""))
                .or(() -> fromBranch(branch))
                .or(() -> subjects.stream().findFirst().flatMap(ChangeDescriptionAnswer::validTitle))
                .orElse(GENERIC_TITLE);
        return new ChangeDescription(title, body(subjects, files));
    }

    /** "task/add-login" gives "Add login". */
    static Optional<String> fromBranch(String branch) {
        var name = branch.strip();
        if (DEFAULT_BRANCHES.contains(name)) {
            return Optional.empty();
        }
        return fromText(name.substring(name.lastIndexOf('/') + 1).replaceAll("[-_]+", " "));
    }

    private static Optional<String> fromText(String text) {
        var words = text.strip().replaceFirst("^#+\\s*", "").replaceAll("\\s+", " ").replaceFirst("[.:;,]+$", "");
        if (words.isEmpty()) {
            return Optional.empty();
        }
        var capitalised = Character.toUpperCase(words.charAt(0)) + words.substring(1);
        var cut = capitalised.length() > ChangeDescriptionAnswer.MAX_TITLE ? capitalised.substring(0, ChangeDescriptionAnswer.MAX_TITLE).strip() : capitalised;
        return Optional.of(cut);
    }

    static String body(List<String> subjects, List<String> files) {
        var parts = new java.util.ArrayList<String>();
        if (!subjects.isEmpty()) {
            parts.add(subjects.stream().map(subject -> "- " + subject).collect(Collectors.joining("\n")));
        }
        if (!files.isEmpty()) {
            var listed = files.stream().limit(MAX_LISTED_FILES).map(file -> "- `" + file + "`").collect(Collectors.joining("\n"));
            var more = files.size() > MAX_LISTED_FILES ? "\n- and " + (files.size() - MAX_LISTED_FILES) + " more" : "";
            parts.add("Changed files:\n" + listed + more);
        }
        return String.join("\n\n", parts);
    }
}
