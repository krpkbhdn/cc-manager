package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class UpdatePresetTool extends PresetTool {

    UpdatePresetTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "update_preset";
    }

    @Override
    public String description() {
        return "Renames a preset and/or changes its description; omitted fields stay as they are.";
    }

    @Override
    public String inputSchema() {
        return schema(PRESET_ID_PROPERTY + ",\"name\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}", "\"preset_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var current = requiredPreset(arguments);
        var name = arguments.hasNonNull("name") ? arguments.path("name").asString() : current.name();
        var description = arguments.hasNonNull("description") ? arguments.path("description").asString() : current.description();
        return "Preset " + describe(presets.update(current.id(), name, description)) + " updated.";
    }
}
