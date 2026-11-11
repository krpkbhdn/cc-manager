package com.github.krpkbhdn.ccmanager.security;

import java.util.regex.Pattern;

/** Secret patterns shared by everything that stores or indexes hook and transcript text. */
public final class SecretPatterns {

    /** Masks only key=value, key: value and bearer forms; space-separated flags and bare tokens (sk-...) pass through. */
    public static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)((?:(?:token|secret|passw(?:or)?d|api[_-]?key|credential)[\\w-]*+\"?\\s*[=:]\\s*|bearer\\s+))(\"[^\"]*\"|'[^']*'|\\S+)");

    /** Credential file names without anchors or wildcards that match whole lines, so free text can reuse them too. */
    public static final String CREDENTIAL_NAMES = "\\.git-credentials|\\.htpasswd|client_secret[\\w.-]*\\.json|[\\w.-]*\\.tfstate(?:\\.backup)?|\\.kube[/\\\\]config";

    private SecretPatterns() {
    }
}
