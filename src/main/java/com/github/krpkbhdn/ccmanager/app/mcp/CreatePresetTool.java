package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class CreatePresetTool extends PresetTool {

    CreatePresetTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "create_preset";
    }

    @Override
    public String description() {
        return "Creates an empty project preset; add its components with add_preset_component.";
    }

    @Override
    public String inputSchema() {
        return schema("\"name\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}", "\"name\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var preset = presets.create(McpToolDescriptor.requiredText(arguments, "name"), arguments.path("description").asString(""));
        return "Preset " + describe(preset) + " created.";
    }
}
