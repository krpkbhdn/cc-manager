package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Runs claude mcp list, which can start the servers a project declares, so it always goes through the permission dialog. */
@Component
class CheckMcpHealthTool extends McpServerTool {

    private final McpService mcp;

    CheckMcpHealthTool(McpService mcp, ProjectService projects) {
        super(projects);
        this.mcp = mcp;
    }

    @Override
    public String name() {
        return "check_mcp_health";
    }

    @Override
    public String description() {
        return "Connection status of one MCP server. Runs claude mcp list, which can start the servers declared in the project, so use it only when asked.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + "},\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = requiredName(arguments);
        var health = mcp.health(projectDirectory(arguments)).stream().filter(h -> h.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + name + " (see list_mcp_servers)"));
        var result = new LinkedHashMap<String, Object>();
        result.put("name", name);
        result.put("target", McpTargetRedaction.redact(health.target()));
        result.put("status", health.status().name());
        result.put("statusText", health.statusText());
        return json(result);
    }
}
