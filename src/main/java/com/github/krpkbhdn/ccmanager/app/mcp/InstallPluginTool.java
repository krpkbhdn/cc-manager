package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Never accepts a marketplace command: when one is pending the user finishes the install in the Plugins screen. */
@Component
class InstallPluginTool extends PluginTool {

    InstallPluginTool(ProjectService projects, PluginService plugins) {
        super(projects, plugins);
    }

    @Override
    public String name() {
        return "install_plugin";
    }

    @Override
    public String description() {
        return "Installs a plugin from an added marketplace; a plugin that runs a command is left for the user to confirm in the Plugins screen.";
    }

    @Override
    public String inputSchema() {
        return pluginSchema();
    }

    @Override
    protected String execute(JsonNode arguments) {
        var id = McpToolDescriptor.requiredText(arguments, "plugin");
        var result = plugins.install(id, scopeOf(arguments), projectDirectory(arguments));
        if (result.needsConfirmation()) {
            return "Plugin " + id + " was NOT installed: its marketplace runs the command '" + result.pendingCommand().command()
                    + "'. Tell the user to review and confirm it in the Plugins screen.";
        }
        return "Plugin " + id + " installed. " + result.message();
    }
}
