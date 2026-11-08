package com.github.krpkbhdn.ccmanager.claude.hook;

import java.util.regex.Pattern;

/** Secret patterns shared by everything that stores or indexes hook and transcript text. */
public final class SecretPatterns {

    /** Masks only key=value, key: value and bearer forms; space-separated flags and bare tokens (sk-...) pass through. */
    public static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)((?:(?:token|secret|passw(?:or)?d|api[_-]?key|credential)[\\w-]*+\"?\\s*[=:]\\s*|bearer\\s+))(\"[^\"]*\"|'[^']*'|\\S+)");

    private SecretPatterns() {
    }
}
