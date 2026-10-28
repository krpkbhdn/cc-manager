package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SetPluginEnabledTool extends PluginTool {

    SetPluginEnabledTool(ProjectService projects, PluginService plugins) {
        super(projects, plugins);
    }

    @Override
    public String name() {
        return "set_plugin_enabled";
    }

    @Override
    public String description() {
        return "Enables or disables an installed plugin in a scope.";
    }

    @Override
    public String inputSchema() {
        return pluginSchema("\"enabled\":{\"type\":\"boolean\"}").replace("[\"plugin\"]", "[\"plugin\",\"enabled\"]");
    }

    @Override
    protected String execute(JsonNode arguments) {
        var id = McpToolDescriptor.requiredText(arguments, "plugin");
        if (!arguments.path("enabled").isBoolean()) {
            throw new IllegalArgumentException("Argument 'enabled' must be true or false");
        }
        var enabled = arguments.path("enabled").asBoolean();
        plugins.setEnabled(id, scopeOf(arguments), projectDirectory(arguments), enabled);
        return "Plugin " + id + (enabled ? " enabled." : " disabled.");
    }
}
