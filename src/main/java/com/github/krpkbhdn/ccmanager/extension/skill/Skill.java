package com.github.krpkbhdn.ccmanager.extension.skill;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import java.nio.file.Path;

/**
 * A skill and its SKILL.md. A plugin skill carries the plugin id (scope is then the plugin's scope) and is read-only;
 * pluginId is empty for the user's and the project's own skills.
 */
public record Skill(String name, String description, ExtensionScope scope, String pluginId, Path file) {

    public boolean readOnly() {
        return !pluginId.isEmpty();
    }

    public Path directory() {
        return file.getParent();
    }
}
