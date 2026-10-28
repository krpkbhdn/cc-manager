package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListMcpServersTool extends McpServerTool {

    private final McpService mcp;

    ListMcpServersTool(McpService mcp, ProjectService projects) {
        super(projects);
        this.mcp = mcp;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_mcp_servers";
    }

    @Override
    public String description() {
        return "Lists the configured MCP servers (all scopes visible from the project when one is given, else user scope) without env or headers.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + OPTIONAL_PROJECT_PROPERTY + "}}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var servers = mcp.servers(projectDirectory(arguments));
        if (servers.isEmpty()) {
            return "No MCP servers configured.";
        }
        return servers.stream().map(s -> s.name() + " [" + s.scope().cliValue() + ", " + s.transport() + "] "
                + McpTargetRedaction.redact(s.target()) + " approval=" + s.approval()).collect(Collectors.joining("\n"));
    }
}
