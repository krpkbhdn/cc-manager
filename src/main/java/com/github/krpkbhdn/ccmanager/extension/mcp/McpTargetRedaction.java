package com.github.krpkbhdn.ccmanager.extension.mcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * The one masking rule for MCP server targets and marketplace references: a URL shows scheme, host and port only
 * (credentials, path and query can all carry secrets), anything else only its executable.
 */
public final class McpTargetRedaction {

    private static final String URL_MARK = "://";
    private static final Pattern URL = Pattern.compile("^[a-z][a-z0-9+.-]*" + URL_MARK + "\\S+$", Pattern.CASE_INSENSITIVE);
    private static final String PATH_MARKER = "/…";
    private static final String HIDDEN = "<redacted>";

    private McpTargetRedaction() {}

    public static String redact(String target) {
        var text = target == null ? "" : target.strip();
        return URL.matcher(text).matches() ? redactUrl(text) : executable(text);
    }

    private static String redactUrl(String url) {
        try {
            var uri = new URI(url);
            if (uri.getHost() != null) {
                var port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
                return uri.getScheme() + URL_MARK + uri.getHost() + port + PATH_MARKER;
            }
        } catch (URISyntaxException e) {
            // an unparsable URL is a single token that may itself be the secret, so only its scheme stays
        }
        return url.substring(0, url.indexOf(URL_MARK)) + URL_MARK + HIDDEN;
    }

    private static String executable(String command) {
        return command.split("\\s+", 2)[0];
    }
}
