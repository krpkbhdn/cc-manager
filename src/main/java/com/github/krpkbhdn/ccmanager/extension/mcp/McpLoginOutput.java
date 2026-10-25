package com.github.krpkbhdn.ccmanager.extension.mcp;

import java.util.Optional;
import java.util.regex.Pattern;

/** Reads the terminal output of {@code claude mcp login}: the fallback sign-in page and the lines worth showing on failure. */
final class McpLoginOutput {

    private static final Pattern SIGN_IN_URL = Pattern.compile("If the browser didn't open, visit:\\s+(https?://\\S+?)(?=Waiting|\\s|$)");
    private static final String SDK_NOISE = "[mcp-sdk]";
    private static final String WAITING = "Waiting for authorization";
    private static final String BROWSER_HINT = "If the browser didn't open";

    private McpLoginOutput() {}

    static Optional<String> signInUrl(String output) {
        var match = SIGN_IN_URL.matcher(output);
        return match.find() ? Optional.of(match.group(1)) : Optional.empty();
    }

    /** The output without SDK noise, the waiting prompt and the sign-in URL (which carries one-time state). */
    static String failureText(String output) {
        return output.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith(SDK_NOISE) && !line.startsWith(WAITING)
                        && !line.startsWith(BROWSER_HINT) && !line.startsWith("http"))
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
    }
}
