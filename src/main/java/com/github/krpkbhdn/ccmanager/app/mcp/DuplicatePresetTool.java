package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class DuplicatePresetTool extends PresetTool {

    DuplicatePresetTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "duplicate_preset";
    }

    @Override
    public String description() {
        return "Copies a preset with all its components under a new name.";
    }

    @Override
    public String inputSchema() {
        return schema(PRESET_ID_PROPERTY + ",\"name\":{\"type\":\"string\",\"description\":\"Name of the copy\"}", "\"preset_id\",\"name\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var source = requiredPreset(arguments);
        var copy = presets.duplicate(source.id(), McpToolDescriptor.requiredText(arguments, "name"));
        return "Preset " + describe(source) + " copied to " + describe(copy) + ".";
    }
}
