package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class RemovePresetComponentTool extends PresetTool {

    RemovePresetComponentTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "remove_preset_component";
    }

    @Override
    public String description() {
        return "Removes a component from its preset.";
    }

    @Override
    public String inputSchema() {
        return schema(COMPONENT_ID_PROPERTY, "\"component_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var component = requiredComponent(arguments);
        presets.removeComponent(component.id());
        return "Removed %s '%s' (component %d).".formatted(component.kind(), component.name(), component.id());
    }
}
