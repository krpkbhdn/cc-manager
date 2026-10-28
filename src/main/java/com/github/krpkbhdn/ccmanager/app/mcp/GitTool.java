package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.git.GitException;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base of the Assistant's git tools: resolves the numeric {@code project} argument to its repository and turns git failures into tool errors. */
abstract class GitTool implements AssistantMcpTool {

    static final String PROJECT_PROPERTY = "\"project\":{\"type\":\"integer\",\"description\":\"Project id\"}";

    private final ProjectService projects;
    private final GitStatusService status;
    final JsonMapper mapper;

    GitTool(ProjectService projects, GitStatusService status, JsonMapper mapper) {
        this.projects = projects;
        this.status = status;
        this.mapper = mapper;
    }

    /** @param repo the repository root of the project */
    abstract String run(Path repo, JsonNode arguments);

    @Override
    public String call(JsonNode arguments) {
        var id = arguments.path("project").asLong(0);
        var project = projects.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown project id: " + id + "; pass the numeric 'project' id"));
        try {
            return run(status.topLevel(project.path()), arguments);
        } catch (GitException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    static String requiredText(JsonNode arguments, String field) {
        return McpToolDescriptor.requiredText(arguments, field);
    }

    static boolean flag(JsonNode arguments, String field) {
        return arguments.path(field).asBoolean(false);
    }

    static String optionalText(JsonNode arguments, String field) {
        return arguments.path(field).asString("").strip();
    }
}
