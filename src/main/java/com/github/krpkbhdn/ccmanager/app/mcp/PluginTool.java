package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;

/** Base of the plugin tools: the plugin id argument and the shared service. */
abstract class PluginTool extends ExtensionScopeTool {

    static final String PLUGIN_PROPERTY = "\"plugin\":{\"type\":\"string\",\"description\":\"Plugin id name@marketplace from list_plugins\"}";

    final PluginService plugins;

    PluginTool(ProjectService projects, PluginService plugins) {
        super(projects);
        this.plugins = plugins;
    }

    static String pluginSchema(String... extraProperties) {
        var properties = new StringBuilder(PLUGIN_PROPERTY).append(',').append(OPTIONAL_PROJECT_PROPERTY).append(',').append(SCOPE_PROPERTY);
        for (var extra : extraProperties) {
            properties.append(',').append(extra);
        }
        return "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[\"plugin\"]}";
    }
}
