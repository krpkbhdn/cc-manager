package com.github.krpkbhdn.ccmanager.author;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** The bundled manifest of the author's setup, read from {@code author-profile/profile.json}. */
public record AuthorProfile(List<String> files, String commonPrompt, ScopeSetup userSetup, ScopeSetup projectSetup, TeamsSetup teams) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public AuthorProfile {
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** @throws IllegalArgumentException when the stream is not a manifest */
    public static AuthorProfile parse(InputStream json) {
        try {
            return MAPPER.readValue(json, AuthorProfile.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a valid manifest: " + e.getMessage(), e);
        }
    }

    public int version(AuthorComponent component) {
        return switch (component) {
            case USER_SETUP -> userSetup.version();
            case PROJECT_SETUP -> projectSetup.version();
            case TEAMS -> teams.version();
        };
    }

    /** Notes of the versions after {@code installedVersion} up to the bundled one, oldest first. */
    public List<String> changesSince(AuthorComponent component, int installedVersion) {
        var bundled = version(component);
        var changes = switch (component) {
            case USER_SETUP -> userSetup.changes();
            case PROJECT_SETUP -> projectSetup.changes();
            case TEAMS -> teams.changes();
        };
        return changes.entrySet().stream()
                .filter(note -> note.getKey() > installedVersion && note.getKey() <= bundled)
                .sorted(Map.Entry.comparingByKey())
                .flatMap(note -> note.getValue().stream())
                .toList();
    }

    public String title(AuthorComponent component) {
        return switch (component) {
            case USER_SETUP -> userSetup.title();
            case PROJECT_SETUP -> projectSetup.title();
            case TEAMS -> teams.title();
        };
    }

    public String summary(AuthorComponent component) {
        return switch (component) {
            case USER_SETUP -> userSetup.summary();
            case PROJECT_SETUP -> projectSetup.summary();
            case TEAMS -> teams.summary();
        };
    }
}
