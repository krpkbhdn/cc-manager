package com.github.krpkbhdn.ccmanager.extension;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Where Claude Code stores an extension: for the user, shared in the project, or private to this project. */
public enum ExtensionScope {
    USER, PROJECT, LOCAL;

    /** The value of the CLI's {@code --scope} option. */
    public String cliValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the directory a scoped CLI command must run in: the project, or null for user scope */
    public Path workingDirectory(Path projectDirectory) {
        if (this == USER) {
            return null;
        }
        if (projectDirectory == null) {
            throw new ExtensionException(cliValue() + " scope needs a project");
        }
        return projectDirectory;
    }

    public static Optional<ExtensionScope> fromCli(String value) {
        for (var scope : values()) {
            if (scope.cliValue().equals(value)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
