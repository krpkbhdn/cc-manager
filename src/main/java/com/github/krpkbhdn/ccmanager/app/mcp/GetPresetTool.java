package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class GetPresetTool extends PresetTool {

    GetPresetTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_preset";
    }

    @Override
    public String description() {
        return "Shows a preset with every component (id, kind, name, short preview). MCP configs and settings are redacted to keys; FILE shows path and size only; instruction/skill/agent text is shown. "
                + "With component_id the whole text of one instruction, skill or agent component is returned (a file component still shows path and size only).";
    }

    @Override
    public String inputSchema() {
        return schema(PRESET_ID_PROPERTY + ",\"component_id\":{\"type\":\"integer\",\"description\":\"Optional: read this component completely\"}", "\"preset_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var preset = requiredPreset(arguments);
        if (arguments.hasNonNull("component_id")) {
            var id = ToolArguments.requiredId(arguments, "component_id");
            var component = preset.components().stream().filter(c -> c.id() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Component " + id + " is not in preset " + preset.id()));
            return json(PresetComponentViews.full(component));
        }
        var view = header(preset);
        view.put("components", preset.components().stream().map(PresetComponentViews::summary).toList());
        return json(view);
    }
}
