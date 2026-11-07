package com.github.krpkbhdn.ccmanager.history;

import com.github.krpkbhdn.ccmanager.claude.hook.SecretPatterns;
import com.github.krpkbhdn.ccmanager.git.SecretFiles;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps secrets out of the searchable session history. */
public final class SecretFilter {

    private static final int PATH_MIN_SEGMENTS = 3;
    private static final String REDACTED = "[redacted]";

    private static final List<Pattern> TOKEN_LIKE = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)"),
            Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+"),
            Pattern.compile("sk-[A-Za-z0-9_-]{16,}"),
            Pattern.compile("(?:ghp|gho)_[A-Za-z0-9]{20,}"),
            Pattern.compile("github_pat_[A-Za-z0-9_]{20,}"),
            Pattern.compile("xox[abp]-[A-Za-z0-9-]{10,}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("AIza[\\w-]{35}"),
            Pattern.compile("[sr]k_(?:live|test)_[A-Za-z0-9]{16,}"),
            Pattern.compile("glpat-[\\w-]{20,}"),
            Pattern.compile("npm_[A-Za-z0-9]{36}"));

    private static final int NAME_AFFIX_MAX = 40;

    private static final String QUOTED_OR_BARE = "(?:\"[^\"]*\"|'[^']*'|\\S+)";

    private static final Pattern JSON_KEY = Pattern.compile(
            "(?i)(\\\\?\"[\\w-]*(?:passw(?:or)?d|secret|token|api[_-]?key|private[_-]?key|credential|authorization)[\\w-]*\\\\?\"\\s*:\\s*)"
                    + "(?:\\\\?\"[^\"\\\\]*\\\\?\"|[^\\s,}\\]]+)");

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)[\\w-]{0," + NAME_AFFIX_MAX + "}(?:passw(?:or)?d|pwd|secret|token|api[_-]?key)[\\w-]{0," + NAME_AFFIX_MAX
                    + "}\\s*[:=]\\s*" + QUOTED_OR_BARE
                    + "|(?<![a-z])(?:\\w{0," + NAME_AFFIX_MAX + "}_)?pass\\s*=\\s*" + QUOTED_OR_BARE);

    private static final Pattern PASSWORD_PHRASE = Pattern.compile("(?i)(passw(?:or)?d\\s*:\\s*\\[redacted\\])[^\\r\\n]+");

    private static final Pattern PASSWORD_FLAG = Pattern.compile(
            "(?i)(--?(?:passw(?:or)?d|secret|token|api[_-]?key)\\s+)(?!-)" + QUOTED_OR_BARE);

    private static final Pattern MYSQL_ATTACHED_PASSWORD = Pattern.compile("(\\bmysql\\w*\\b[^\\r\\n|;&]*?\\s-p)(?=\\S)\\S+");

    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?<=://)[^\\s:/@]+:[^\\s@/]+@");

    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*(?:basic|bearer)\\s+)\\S+");

    private static final Pattern HEX_BLOB = Pattern.compile("\\b[0-9a-fA-F]{32,}\\b");

    private static final Pattern LONG_BLOB = Pattern.compile("[A-Za-z0-9+/_-]{40,}={0,2}");

    private static final Pattern PATH_SEGMENT_WORD = Pattern.compile("[a-z]+|[A-Z][a-z]+(?:[A-Z][a-z]+)*");

    private static final Pattern SENSITIVE_FILE = Pattern.compile(
            "(?i)(?:(?<![\\w.-])\\.env(?:\\.[\\w-]+)?(?![\\w-])"
                    + "|\\.(?:pem|key|p12|pfx|keystore|tfvars)(?!\\w)"
                    + "|(?<!\\w)id_(?:rsa|ed25519|ecdsa|dsa)"
                    + "|\\.(?:ssh|aws)[/\\\\]"
                    + "|\\.docker[/\\\\]+config\\.json"
                    + "|\\.(?:netrc|npmrc|pypirc|pgpass)(?!\\w)"
                    + "|(?:" + SecretFiles.CREDENTIAL_NAMES + ")(?![\\w-])"
                    + "|(?<!\\w)credentials"
                    + "|(?<!\\w)secrets\\."
                    + "|kubeconfig"
                    + "|service-account[\\w.-]*\\.json"
                    + "|(?<![\\w.-])printenv(?!\\w)"
                    + "|\"command\"\\s*:\\s*\"\\s*(?:env|set)\\s*(?:[\"|;&>]|\\\\n))");

    private SecretFilter() {
    }

    public static String mask(String text) {
        var masked = JSON_KEY.matcher(text).replaceAll("$1" + REDACTED);
        masked = SecretPatterns.KEY_VALUE.matcher(masked).replaceAll("$1" + REDACTED);
        masked = PASSWORD_PHRASE.matcher(masked).replaceAll("$1");
        masked = PASSWORD_FLAG.matcher(masked).replaceAll("$1" + REDACTED);
        masked = MYSQL_ATTACHED_PASSWORD.matcher(masked).replaceAll("$1" + REDACTED);
        for (Pattern pattern : TOKEN_LIKE) {
            masked = pattern.matcher(masked).replaceAll(REDACTED);
        }
        masked = ASSIGNMENT.matcher(masked).replaceAll(REDACTED);
        masked = URL_CREDENTIALS.matcher(masked).replaceAll(REDACTED + "@");
        masked = AUTHORIZATION_HEADER.matcher(masked).replaceAll("$1" + REDACTED);
        masked = HEX_BLOB.matcher(masked).replaceAll(REDACTED);
        return LONG_BLOB.matcher(masked).replaceAll(blob -> looksLikeSecret(blob.group()) ? REDACTED : blob.group());
    }

    public static boolean touchesSensitiveFile(String toolInputText) {
        return SENSITIVE_FILE.matcher(toolInputText).find();
    }

    /** A random blob mixes digits with both letter cases; a path or identifier of plain words does not. */
    private static boolean looksLikeSecret(String blob) {
        var mixed = blob.chars().anyMatch(Character::isDigit) && blob.chars().anyMatch(Character::isLowerCase)
                && blob.chars().anyMatch(Character::isUpperCase);
        return mixed && !isPath(blob);
    }

    private static boolean isPath(String blob) {
        var segments = blob.split("/");
        if (segments.length < PATH_MIN_SEGMENTS) {
            return false;
        }
        var words = Arrays.stream(segments).filter(segment -> PATH_SEGMENT_WORD.matcher(segment).matches()).count();
        return words * 2 > segments.length;
    }
}
