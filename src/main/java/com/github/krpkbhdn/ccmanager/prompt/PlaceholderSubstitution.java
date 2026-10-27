package com.github.krpkbhdn.ccmanager.prompt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces {project}, {branch} and {file}; unknown placeholders and placeholders without a value stay literal text. */
public final class PlaceholderSubstitution {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(project|branch|file)}");

    private PlaceholderSubstitution() {}

    public static String apply(String text, PlaceholderValues values) {
        return PLACEHOLDER.matcher(text).replaceAll(match -> {
            var value = switch (match.group(1)) {
                case "project" -> values.project();
                case "branch" -> values.branch();
                default -> values.file();
            };
            return Matcher.quoteReplacement(value == null ? match.group() : value);
        });
    }
}
