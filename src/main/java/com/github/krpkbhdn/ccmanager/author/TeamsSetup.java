package com.github.krpkbhdn.ccmanager.author;

import java.util.List;

/** The versioned set of agent teams; {@code templates} are profile paths of team JSON files. */
public record TeamsSetup(int version, String title, String summary, List<String> templates) {

    public TeamsSetup {
        templates = templates == null ? List.of() : List.copyOf(templates);
    }
}
