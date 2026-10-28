package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class RemoveMarketplaceTool extends ExtensionScopeTool {

    private final PluginService plugins;

    RemoveMarketplaceTool(ProjectService projects, PluginService plugins) {
        super(projects);
        this.plugins = plugins;
    }

    @Override
    public String name() {
        return "remove_marketplace";
    }

    @Override
    public String description() {
        return "Removes an added plugin marketplace by its name from list_plugins.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Marketplace name\"}},\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = McpToolDescriptor.requiredText(arguments, "name");
        plugins.removeMarketplace(name);
        return "Marketplace " + name + " removed.";
    }
}
