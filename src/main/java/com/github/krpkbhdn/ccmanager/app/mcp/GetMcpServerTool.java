package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Never returns {@code claude mcp get} output: it can hold secrets. Shows the parsed config and, on request, health. */
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
                + "health=true also runs claude mcp list, which can start the servers, so use it only when asked.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY
                + ",\"health\":{\"type\":\"boolean\",\"description\":\"Also check the connection status (slow, may start servers); default false\"}},"
                + "\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = requiredName(arguments);
        var directory = projectDirectory(arguments);
        var configs = mcp.servers(directory).stream().filter(s -> s.name().equals(name)).toList();
        var health = arguments.path("health").asBoolean(false)
                ? mcp.health(directory).stream().filter(h -> h.name().equals(name)).findFirst()
                : Optional.<McpHealth>empty();
        if (configs.isEmpty() && health.isEmpty()) {
            throw new IllegalArgumentException("MCP server not found: " + name + " (see list_mcp_servers)");
        }
        var entries = new ArrayList<Map<String, Object>>();
        configs.forEach(config -> entries.add(describe(name, config, health)));
        if (configs.isEmpty()) {
            entries.add(describeHealthOnly(name, health.orElseThrow()));
        }
        return json(entries.size() == 1 ? entries.get(0) : entries);
    }

    private static Map<String, Object> describe(String name, McpServerConfig config, Optional<McpHealth> health) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", name);
        result.put("scope", config.scope().cliValue());
        result.put("transport", config.transport());
        result.put("target", McpTargetRedaction.redact(config.target()));
        result.put("approval", config.approval().name());
        health.ifPresent(h -> putHealth(result, h));
        return result;
    }

    private static Map<String, Object> describeHealthOnly(String name, McpHealth health) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", name);
        result.put("target", McpTargetRedaction.redact(health.target()));
        putHealth(result, health);
        return result;
    }

    private static void putHealth(Map<String, Object> result, McpHealth health) {
        result.put("status", health.status().name());
        result.put("statusText", health.statusText());
    }
}
