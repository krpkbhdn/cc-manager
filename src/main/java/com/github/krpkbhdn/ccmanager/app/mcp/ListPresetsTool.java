package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListPresetsTool extends PresetTool {

    ListPresetsTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_presets";
    }

    @Override
    public String description() {
        return "Lists the project presets: id, name, description, bundled key (null for your own), edited flag, revision and the number of components per kind.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String call(JsonNode arguments) {
        return json(presets.list().stream().map(preset -> {
            var view = header(preset);
            view.put("components", preset.components().stream().collect(Collectors.groupingBy(PresetComponent::kind, TreeMap::new, Collectors.counting())));
            return view;
        }).toList());
    }
}
