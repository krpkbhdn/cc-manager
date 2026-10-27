package com.github.krpkbhdn.ccmanager.author;

import java.util.List;
import java.util.Map;

/** The versioned set of agent teams; {@code templates} are profile paths of team JSON files, {@code changes} maps a version to its notes. */
public record TeamsSetup(int version, String title, String summary, List<String> templates, Map<Integer, List<String>> changes) {

    public TeamsSetup {
        templates = templates == null ? List.of() : List.copyOf(templates);
        changes = changes == null ? Map.of() : Map.copyOf(changes);
    }
}
