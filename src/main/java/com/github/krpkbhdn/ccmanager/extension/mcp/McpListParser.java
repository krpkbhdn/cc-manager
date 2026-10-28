package com.github.krpkbhdn.ccmanager.extension.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Parser of {@code claude mcp list} lines {@code name: target - <symbol> <status>}; other lines are skipped. */
final class McpListParser {

    private static final String NAME_END = ": ";
    private static final String STATUS_START = " - ";
    private static final Pattern TRANSPORT_SUFFIX = Pattern.compile("\\s+\\([A-Za-z]+\\)$");

    private McpListParser() {}

    static List<McpHealth> parse(String output) {
        var servers = new ArrayList<McpHealth>();
        for (var line : output.lines().toList()) {
            var nameEnd = line.indexOf(NAME_END);
            var statusStart = line.lastIndexOf(STATUS_START);
            if (nameEnd > 0 && statusStart > nameEnd) {
                var statusText = line.substring(statusStart + STATUS_START.length()).replaceFirst("^[^\\p{L}]+", "").strip();
                servers.add(new McpHealth(line.substring(0, nameEnd).strip(),
                        target(line.substring(nameEnd + NAME_END.length(), statusStart)), status(statusText), statusText));
            }
        }
        return List.copyOf(servers);
    }

    private static String target(String text) {
        return McpTargetRedaction.redact(TRANSPORT_SUFFIX.matcher(text.strip()).replaceFirst(""));
    }

    private static McpStatus status(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("auth")) {
            return McpStatus.NEEDS_AUTHENTICATION;
        }
        if (lower.contains("fail") || lower.contains("error")) {
            return McpStatus.FAILED;
        }
        if (lower.startsWith("connected")) {
            return McpStatus.CONNECTED;
        }
        return lower.contains("pending") || lower.contains("connecting") ? McpStatus.PENDING : McpStatus.UNKNOWN;
    }
}
