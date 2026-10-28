package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class RemoveMcpServerTool extends McpServerTool {

    private final McpService mcp;

    RemoveMcpServerTool(McpService mcp, ProjectService projects) {
        super(projects);
        this.mcp = mcp;
    }

    @Override
    public String name() {
        return "remove_mcp_server";
    }

    @Override
    public String description() {
        return "Removes an MCP server from the given scope.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY + "," + MCP_SCOPE_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY
                + "},\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = requiredName(arguments);
        var scope = scopeOf(arguments);
        var directory = projectDirectory(arguments);
        mcp.remove(name, scope, directory);
        return "MCP server '" + name + "' removed from " + scope.cliValue() + " scope.";
    }
}
