package com.github.krpkbhdn.ccmanager.git;

import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/** Accepts only a real description from the claude answer: a JSON object with a one-line title, never a refusal or prose. */
final class ChangeDescriptionAnswer {

    static final int MAX_TITLE = 72;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MAX_BODY = 8000;
    private static final Pattern REFUSAL = Pattern.compile(
            "^(i can't|i cannot|i can not|i'm unable|i am unable|i'm not able|i am not able|i don't|i do not|i'm sorry|i am sorry|i apologi[sz]e|sorry|as an ai"
                    + "|unfortunately|there (are|is) no (changes|commits|diff)|no (changes|commits)\\b|here is|here's|sure[,!.]|certainly[,!.])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern WRAPPING_FENCE = Pattern.compile("\\A```[\\w-]*\\R(.*?)\\R?```\\z", Pattern.DOTALL);
    private static final Pattern TITLE_PREFIX = Pattern.compile("^(title\\s*:\\s*|#+\\s*)", Pattern.CASE_INSENSITIVE);
    private static final String QUOTES = "\"'`";

    private ChangeDescriptionAnswer() {}

    /** @param stdout of {@code claude -p --output-format json}; empty when it holds no usable description */
    static Optional<ChangeDescription> parse(String stdout) {
        try {
            var root = JSON.readTree(stdout);
            if (root.path("is_error").asBoolean(false)) {
                return Optional.empty();
            }
            return parseResult(root.path("result").asString(""));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static Optional<ChangeDescription> parseResult(String result) {
        var start = result.indexOf('{');
        var end = result.lastIndexOf('}');
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        try {
            var object = JSON.readTree(result.substring(start, end + 1));
            return validTitle(object.path("title").asString("")).map(title -> new ChangeDescription(title, validBody(object.path("body").asString(""))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static Optional<String> validTitle(String raw) {
        var title = raw.strip();
        if (title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            return Optional.empty();
        }
        title = TITLE_PREFIX.matcher(title).replaceFirst("").strip();
        while (title.length() > 1 && QUOTES.indexOf(title.charAt(0)) >= 0 && title.charAt(0) == title.charAt(title.length() - 1)) {
            title = title.substring(1, title.length() - 1).strip();
        }
        boolean usable = !title.isEmpty() && title.length() <= MAX_TITLE && !title.equalsIgnoreCase("none") && !REFUSAL.matcher(title).find();
        return usable ? Optional.of(title) : Optional.empty();
    }

    /** The cleaned body, or empty when it is too long or reads like a refusal; the caller then uses the deterministic body. */
    static String validBody(String raw) {
        var body = HTML_COMMENT.matcher(raw).replaceAll("").strip();
        var fenced = WRAPPING_FENCE.matcher(body);
        if (fenced.matches()) {
            body = fenced.group(1).strip();
        }
        return body.length() > MAX_BODY || REFUSAL.matcher(body).find() || body.equalsIgnoreCase("none") ? "" : body;
    }
}
