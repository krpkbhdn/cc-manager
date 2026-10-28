package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListPluginsTool extends ExtensionScopeTool {

    private final PluginService plugins;

    ListPluginsTool(ProjectService projects, PluginService plugins) {
        super(projects);
        this.plugins = plugins;
    }

    private static Marketplace masked(Marketplace marketplace) {
        return new Marketplace(marketplace.name(), marketplace.sourceKind(), McpTargetRedaction.redact(marketplace.reference()), marketplace.installLocation());
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_plugins";
    }

    @Override
    public String description() {
        return "Lists installed plugins and added marketplaces; available=true also lists the plugins the marketplaces offer (a long list).";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"available\":{\"type\":\"boolean\",\"description\":\"Include installable plugins\"}}}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var result = new LinkedHashMap<String, Object>();
        result.put("installed", plugins.installed());
        result.put("marketplaces", plugins.marketplaces().stream().map(ListPluginsTool::masked).toList());
        if (arguments.path("available").asBoolean(false)) {
            result.put("available", plugins.available());
        }
        return json(result);
    }
}
