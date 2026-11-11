package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class DeletePresetTool extends PresetTool {

    DeletePresetTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "delete_preset";
    }

    @Override
    public String description() {
        return "Deletes a preset; a bundled one is only hidden and can be brought back with restore_preset.";
    }

    @Override
    public String inputSchema() {
        return schema(PRESET_ID_PROPERTY, "\"preset_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var preset = requiredPreset(arguments);
        presets.delete(preset.id());
        return "Preset " + describe(preset) + " deleted.";
    }
}
