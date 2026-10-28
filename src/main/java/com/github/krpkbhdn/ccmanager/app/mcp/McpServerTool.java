package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import tools.jackson.databind.JsonNode;

/** Base of the MCP server tools: the shared project and scope arguments plus the server name. */
abstract class McpServerTool extends ExtensionScopeTool {

    static final String NAME_PROPERTY = "\"name\":{\"type\":\"string\",\"description\":\"MCP server name\"}";
    static final String MCP_SCOPE_PROPERTY =
            "\"scope\":{\"type\":\"string\",\"enum\":[\"user\",\"project\",\"local\"],\"description\":\"Default: local when a project is given, else user\"}";

    McpServerTool(ProjectService projects) {
        super(projects);
    }

    /** Local scope is private to the user, so an MCP server is never shared with the team unless asked. */
    @Override
    ExtensionScope defaultProjectScope() {
        return ExtensionScope.LOCAL;
    }

    static String requiredName(JsonNode arguments) {
        var name = arguments.path("name").asString("");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Argument 'name' is required");
        }
        return name;
    }
}
