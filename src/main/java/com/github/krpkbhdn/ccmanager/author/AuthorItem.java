package com.github.krpkbhdn.ccmanager.author;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * One separately versioned, selectable piece of the author's setup. The payload fields are used per kind: {@code name} and
 * {@code source} (marketplace), {@code name} (plugin as {@code name@marketplace}, skill, MCP server, team), {@code path} (skill directory),
 * {@code config} (MCP server), {@code template} (team), {@code fragment} (settings: a JSON object merged into the user settings.json).
 * {@code changes} maps a version to the notes of what it added.
 */
public record AuthorItem(String id, AuthorArea area, String group, String title, String description, AuthorKind kind,
        int version, List<String> requires, Map<Integer, List<String>> changes, String name, String source, String path, JsonNode config,
        String template, JsonNode fragment) {

    public AuthorItem {
        requires = requires == null ? List.of() : List.copyOf(requires);
        changes = changes == null ? Map.of() : Map.copyOf(changes);
    }

    /** Notes of the versions after {@code installedVersion} up to the bundled one, oldest first. */
    public List<String> changesSince(int installedVersion) {
        return changes.entrySet().stream()
                .filter(note -> note.getKey() > installedVersion && note.getKey() <= version)
                .sorted(Map.Entry.comparingByKey())
                .flatMap(note -> note.getValue().stream())
                .toList();
    }
}
