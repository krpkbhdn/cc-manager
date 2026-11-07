package com.github.krpkbhdn.ccmanager.git;

import java.util.regex.Pattern;

/** Names that look like keys, credentials or env files; their content must never be shown to a model. */
public final class SecretFiles {

    /** Credential names without anchors or wildcards that match whole lines, so free text can reuse them too. */
    public static final String CREDENTIAL_NAMES = "\\.git-credentials|\\.htpasswd|client_secret[\\w.-]*\\.json|[\\w.-]*\\.tfstate(?:\\.backup)?|\\.kube[/\\\\]config";

    private static final Pattern SECRET_FILE = Pattern.compile(
            "(^|[/\\\\])("
                    + "\\.env(\\..*)?|.*\\.env|\\.npmrc|\\.netrc|\\.pgpass|id_(rsa|dsa|ecdsa|ed25519)|credentials.*|secrets\\..*|service-account.*\\.json"
                    + "|" + CREDENTIAL_NAMES
                    + "|.*\\.(pem|key|p12|pfx|keystore|jks|ppk|kdbx|tfvars)"
                    + ")$",
            Pattern.CASE_INSENSITIVE);

    private SecretFiles() {}

    public static boolean looksSecret(String path) {
        return SECRET_FILE.matcher(path).find();
    }
}
