package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class AddMcpServerTool extends McpServerTool {

    private final McpService mcp;

    AddMcpServerTool(McpService mcp, ProjectService projects) {
        super(projects);
        this.mcp = mcp;
    }

    @Override
    public String name() {
        return "add_mcp_server";
    }

    @Override
    public String description() {
        return "Adds an MCP server from a JSON config such as {\"type\":\"http\",\"url\":\"...\"}; sessions started afterwards load it.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY
                + ",\"config\":{\"type\":\"object\",\"description\":\"Server config: type http or sse with url (and headers), or stdio with command, args, env\"},"
                + MCP_SCOPE_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + "},\"required\":[\"name\",\"config\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = requiredName(arguments);
        var config = arguments.path("config");
        if (!config.isObject()) {
            throw new IllegalArgumentException("Argument 'config' must be a JSON object");
        }
        var scope = scopeOf(arguments);
        var directory = projectDirectory(arguments);
        mcp.add(name, config.toString(), scope, directory);
        return "MCP server '" + name + "' added to " + scope.cliValue() + " scope.";
    }
}
