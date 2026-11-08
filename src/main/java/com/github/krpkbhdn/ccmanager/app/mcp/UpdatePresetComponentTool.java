package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class UpdatePresetComponentTool extends PresetTool {

    UpdatePresetComponentTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "update_preset_component";
    }

    @Override
    public String description() {
        return "Changes a component: a new name and/or the payload fields of its kind (same arguments as add_preset_component); omitted fields stay, the kind cannot change.";
    }

    @Override
    public String inputSchema() {
        return schema(COMPONENT_ID_PROPERTY + ",\"name\":{\"type\":\"string\"}," + PresetPayloads.PROPERTIES, "\"component_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var current = requiredComponent(arguments);
        var name = arguments.hasNonNull("name") ? arguments.path("name").asString() : current.name();
        var updated = presets.updateComponent(current.id(), name, PresetPayloads.build(current.kind(), name, arguments, current.payload()));
        return "Updated %s '%s' (component %d).".formatted(updated.kind(), updated.name(), updated.id());
    }
}
