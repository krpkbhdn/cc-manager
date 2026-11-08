package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.claude.hook.SecretPatterns;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The only way an exception text reaches a plan line or an outcome: CLI errors can echo source URLs with credentials and secret values. */
final class ErrorText {

    private static final Pattern URL = Pattern.compile("[a-z][a-z0-9+.-]*://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final int MAX_LENGTH = 300;

    private ErrorText() {
    }

    static String redacted(Throwable error) {
        var message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        var urlsHidden = URL.matcher(message).replaceAll(match -> Matcher.quoteReplacement(McpTargetRedaction.redact(match.group())));
        var masked = SecretPatterns.KEY_VALUE.matcher(urlsHidden).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "<secret>"));
        var text = masked.strip();
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) + "…" : text;
    }
}
