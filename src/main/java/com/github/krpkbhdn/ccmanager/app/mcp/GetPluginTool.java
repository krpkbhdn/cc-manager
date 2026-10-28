package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class GetPluginTool extends PluginTool {

    GetPluginTool(ProjectService projects, PluginService plugins) {
        super(projects, plugins);
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_plugin";
    }

    @Override
    public String description() {
        return "Details of a plugin: description, components it ships and the tokens it adds to every session.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PLUGIN_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + "},\"required\":[\"plugin\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        return json(plugins.details(McpToolDescriptor.requiredText(arguments, "plugin"), projectDirectory(arguments)));
    }
}
