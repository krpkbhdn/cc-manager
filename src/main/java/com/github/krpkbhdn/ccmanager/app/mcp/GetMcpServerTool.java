package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Never returns {@code claude mcp get} output (it can hold secrets) and never runs the health check (it can start servers). */
@Component
class GetMcpServerTool extends McpServerTool {

    private final McpService mcp;

    GetMcpServerTool(McpService mcp, ProjectService projects) {
        super(projects);
        this.mcp = mcp;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_mcp_server";
    }

    @Override
    public String description() {
        return "Details of one MCP server (every scope that declares the name), without env, headers, URL credentials or path. "
                + "For the connection status use check_mcp_health.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + "},\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = requiredName(arguments);
        var configs = mcp.servers(projectDirectory(arguments)).stream().filter(s -> s.name().equals(name)).toList();
        if (configs.isEmpty()) {
            throw new IllegalArgumentException("MCP server not found: " + name + " (see list_mcp_servers)");
        }
        var entries = configs.stream().map(config -> describe(name, config)).toList();
        return json(entries.size() == 1 ? entries.get(0) : entries);
    }

    private static Map<String, Object> describe(String name, McpServerConfig config) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", name);
        result.put("scope", config.scope().cliValue());
        result.put("transport", config.transport());
        result.put("target", McpTargetRedaction.redact(config.target()));
        result.put("approval", config.approval().name());
        return result;
    }
}
