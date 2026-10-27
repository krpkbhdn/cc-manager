package com.github.krpkbhdn.ccmanager.author;

import java.util.List;
import java.util.Map;

/**
 * One versioned scope of the profile (user or project): what to tell the user, Claude's instructions and the deterministic pre-install;
 * {@code changes} maps a version to the notes of what it added.
 */
public record ScopeSetup(int version, String title, String summary, String prompt, ExtensionSet extensions,
        Map<Integer, List<String>> changes) {

    public ScopeSetup {
        extensions = extensions == null ? new ExtensionSet(null, null, null, null) : extensions;
        changes = changes == null ? Map.of() : Map.copyOf(changes);
    }
}
