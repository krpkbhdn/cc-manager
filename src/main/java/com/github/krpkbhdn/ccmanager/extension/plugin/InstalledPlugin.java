package com.github.krpkbhdn.ccmanager.extension.plugin;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import java.nio.file.Path;

/** One installation of a plugin; projectPath is null for user scope. */
public record InstalledPlugin(
        String id, String version, ExtensionScope scope, boolean enabled, Path installPath, Path projectPath) {}
