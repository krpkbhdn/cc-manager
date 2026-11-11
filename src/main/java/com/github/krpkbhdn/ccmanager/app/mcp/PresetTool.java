package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Base of the Assistant's preset tools: the {@code preset_id} and {@code component_id} arguments and their lookups. */
abstract class PresetTool extends AssistantTool {

    static final String PRESET_ID_PROPERTY = "\"preset_id\":{\"type\":\"integer\",\"description\":\"Preset id from list_presets\"}";
    static final String COMPONENT_ID_PROPERTY = "\"component_id\":{\"type\":\"integer\",\"description\":\"Component id from get_preset\"}";

    protected final ProjectPresetService presets;

    PresetTool(ProjectPresetService presets) {
        this.presets = presets;
    }

    final ProjectPreset requiredPreset(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "preset_id");
        return presets.get(id).orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));
    }

    final PresetComponent requiredComponent(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "component_id");
        return presets.findComponent(id)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + id));
    }

    static String schema(String properties, String required) {
        return "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[" + required + "]}";
    }

    static Map<String, Object> header(ProjectPreset preset) {
        var view = new LinkedHashMap<String, Object>();
        view.put("id", preset.id());
        view.put("name", preset.name());
        view.put("description", preset.description());
        view.put("bundledKey", preset.bundledKey());
        view.put("edited", preset.edited());
        view.put("revision", preset.revision());
        return view;
    }

    static String describe(ProjectPreset preset) {
        return "'%s' (id %d)".formatted(preset.name(), preset.id());
    }
}
