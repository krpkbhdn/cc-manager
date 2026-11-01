package com.github.krpkbhdn.ccmanager.git;

import java.util.regex.Pattern;

/** Names that look like keys, credentials or env files; their content must never be shown to a model. */
public final class SecretFiles {

    private static final Pattern SECRET_FILE = Pattern.compile(
            "(^|[/\\\\])("
                    + "\\.env(\\..*)?|.*\\.env|\\.npmrc|\\.netrc|\\.pgpass|id_(rsa|dsa|ecdsa|ed25519)|credentials.*|secrets\\..*|service-account.*\\.json"
                    + "|.*\\.(pem|key|p12|pfx|keystore|jks|ppk|kdbx|tfvars)"
                    + ")$",
            Pattern.CASE_INSENSITIVE);

    private SecretFiles() {}

    public static boolean looksSecret(String path) {
        return SECRET_FILE.matcher(path).find();
    }
}
