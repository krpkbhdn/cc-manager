package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class AddMarketplaceTool extends ExtensionScopeTool {

    private final PluginService plugins;

    AddMarketplaceTool(ProjectService projects, PluginService plugins) {
        super(projects);
        this.plugins = plugins;
    }

    @Override
    public String name() {
        return "add_marketplace";
    }

    @Override
    public String description() {
        return "Adds a plugin marketplace from a GitHub owner/repo, a git URL or a local path.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"source\":{\"type\":\"string\",\"description\":\"owner/repo, git URL or path\"},"
                + OPTIONAL_PROJECT_PROPERTY + "," + SCOPE_PROPERTY + "},\"required\":[\"source\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var source = McpToolDescriptor.requiredText(arguments, "source");
        plugins.addMarketplace(source, scopeOf(arguments), projectDirectory(arguments));
        return "Marketplace " + McpTargetRedaction.redact(source) + " added.";
    }
}
