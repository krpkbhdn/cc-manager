package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Base of the skill, plugin, marketplace and MCP server tools: the optional {@code project} and {@code scope}
 * arguments. Without a project the user scope applies; with one the default is {@link #defaultProjectScope()}. A
 * group's project directory is its service directory, the session's working directory, so project scope works there
 * too. Extension failures carry a user-readable message; they reach Claude as a tool error instead of an opaque one.
 */
abstract class ExtensionScopeTool extends AssistantTool {

    static final String OPTIONAL_PROJECT_PROPERTY =
            "\"project\":{\"type\":\"integer\",\"description\":\"Project id from list_projects; omit for user-level extensions\"}";
    static final String SCOPE_PROPERTY =
            "\"scope\":{\"type\":\"string\",\"enum\":[\"user\",\"project\",\"local\"],\"description\":\"Default: project when a project is given, else user\"}";

    private final ProjectService projects;

    ExtensionScopeTool(ProjectService projects) {
        this.projects = projects;
    }

    @Override
    public final String call(JsonNode arguments) {
        try {
            return execute(arguments);
        } catch (ExtensionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    protected abstract String execute(JsonNode arguments);

    ExtensionScope defaultProjectScope() {
        return ExtensionScope.PROJECT;
    }

    /** @return the project directory, or null when no project was given */
    final Path projectDirectory(JsonNode arguments) {
        return optionalProject(arguments).map(Project::path).orElse(null);
    }

    final ExtensionScope scopeOf(JsonNode arguments) {
        var text = arguments.path("scope").asString("");
        if (text.isEmpty()) {
            return arguments.hasNonNull("project") ? defaultProjectScope() : ExtensionScope.USER;
        }
        return ExtensionScope.fromCli(text)
                .orElseThrow(() -> new IllegalArgumentException("Argument 'scope' must be user, project or local, got: " + text));
    }

    private Optional<Project> optionalProject(JsonNode arguments) {
        return arguments.hasNonNull("project") ? Optional.of(requiredProject(projects, arguments)) : Optional.empty();
    }
}
