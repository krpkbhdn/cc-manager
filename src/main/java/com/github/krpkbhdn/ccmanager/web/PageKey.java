package com.github.krpkbhdn.ccmanager.web;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Normalised page address: the same page reached by trivially different URLs gets the same key. Only http, https and file are accepted. */
public final class PageKey {

    private static final Map<String, Integer> DEFAULT_PORTS = Map.of("http", 80, "https", 443);
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "file");
    private static final Set<String> TRACKING_PARAMETERS = Set.of("fbclid", "gclid");
    /** A host name (underscores allowed, which {@link URI#getHost} refuses) or a bracketed IPv6 address. */
    private static final String VALID_HOST = "[\\p{L}\\p{N}._-]+|\\[[0-9a-f:.]+]";
    /** Characters Chromium leaves unescaped in a URL but {@link URI} rejects. */
    private static final String UNSAFE_CHARACTERS = "|{}^\"<>";
    /** Whole parameter names, compared without case and without - _ . separators (so apiKey, api-key and api_key are one name). */
    private static final Set<String> SECRET_NAMES = Set.of("token", "accesstoken", "idtoken", "refreshtoken", "key", "apikey", "secret",
            "clientsecret", "password", "passwd", "pwd", "auth", "authorization", "code", "session", "sessionid", "sid", "signature", "sig");
    /** A name ending in one of these (csrf_token, db_password) is secret too. */
    private static final List<String> SECRET_SUFFIXES = List.of("token", "secret", "password", "apikey");
    private static final String REDACTED = "…";
    private static final String TRACKING_PREFIX = "utm_";

    private PageKey() {
    }

    /** @throws IllegalArgumentException for an unparsable URL or any scheme other than http, https and file */
    public static String of(String url) {
        var uri = parse(url);
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        var authority = authorityOf(uri);
        var portStart = portStart(authority);
        var host = (portStart < 0 ? authority : authority.substring(0, portStart)).toLowerCase(Locale.ROOT);
        var port = portStart < 0 ? "" : authority.substring(portStart + 1);
        if (!host.matches(VALID_HOST) && !(host.isEmpty() && scheme.equals("file")) || !port.matches("\\d*")) {
            throw new IllegalArgumentException("The page URL has no valid host.");
        }
        var key = new StringBuilder(scheme).append("://").append(host);
        if (!port.isEmpty() && !port.equals(String.valueOf(DEFAULT_PORTS.get(scheme)))) {
            key.append(':').append(port);
        }
        key.append(path(uri.getRawPath()));
        var query = query(uri.getRawQuery());
        if (!query.isEmpty()) {
            key.append('?').append(query);
        }
        var fragment = uri.getRawFragment();
        if (fragment != null && (fragment.startsWith("/") || fragment.startsWith("!"))) {
            key.append('#').append(fragment);
        }
        return key.toString();
    }

    /**
     * The URL as shown to the user and the agent: no user info, and the values of secret-named query parameters (also in a routed
     * fragment) replaced by an ellipsis. The key is built from the unredacted query and the stored URL stays navigable.
     * @throws IllegalArgumentException as {@link #of}
     */
    public static String redacted(String url) {
        of(url);
        var uri = parse(url);
        var shown = new StringBuilder(uri.getScheme().toLowerCase(Locale.ROOT)).append("://").append(authorityOf(uri))
                .append(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            shown.append('?').append(redactValues(uri.getRawQuery()));
        }
        var fragment = uri.getRawFragment();
        if (fragment != null && (fragment.startsWith("/") || fragment.startsWith("!"))) {
            var queryStart = fragment.indexOf('?');
            shown.append('#').append(queryStart < 0 ? fragment : fragment.substring(0, queryStart + 1) + redactValues(fragment.substring(queryStart + 1)));
        }
        return shown.toString();
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("The page URL is empty.");
        }
        var uri = URI.create(encodeUnsafe(url.strip()));
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("Only http, https and file pages can be reviewed.");
        }
        return uri;
    }

    private static String encodeUnsafe(String url) {
        var out = new StringBuilder(url.length());
        for (var c : url.toCharArray()) {
            if (UNSAFE_CHARACTERS.indexOf(c) >= 0) {
                out.append('%').append(String.format("%02X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String authorityOf(URI uri) {
        var raw = uri.getRawAuthority();
        return raw == null ? "" : raw.substring(raw.lastIndexOf('@') + 1);
    }

    private static int portStart(String authority) {
        var start = authority.lastIndexOf(':');
        return start < authority.lastIndexOf(']') ? -1 : start;
    }

    private static String redactValues(String query) {
        return Arrays.stream(query.split("&", -1)).map(PageKey::redactValue).collect(Collectors.joining("&"));
    }

    private static String redactValue(String parameter) {
        var parts = parameter.split("=", 2);
        return parts.length == 2 && isSecretName(parts[0]) ? parts[0] + "=" + REDACTED : parameter;
    }

    private static boolean isSecretName(String name) {
        var plain = name.toLowerCase(Locale.ROOT).replaceAll("[-_.]", "");
        return SECRET_NAMES.contains(plain) || SECRET_SUFFIXES.stream().anyMatch(plain::endsWith);
    }

    private static String path(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        return rawPath.length() > 1 && rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
    }

    private static String query(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        return Arrays.stream(rawQuery.split("&")).filter(part -> !part.isEmpty()).filter(part -> !isTracking(part)).sorted()
                .collect(Collectors.joining("&"));
    }

    private static boolean isTracking(String parameter) {
        var name = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
        return name.startsWith(TRACKING_PREFIX) || TRACKING_PARAMETERS.contains(name);
    }
}
