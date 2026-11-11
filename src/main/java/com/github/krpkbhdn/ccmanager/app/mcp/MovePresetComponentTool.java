package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class MovePresetComponentTool extends PresetTool {

    MovePresetComponentTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "move_preset_component";
    }

    @Override
    public String description() {
        return "Moves a component one place up or down in its preset; at the end of the list nothing happens.";
    }

    @Override
    public String inputSchema() {
        return schema(COMPONENT_ID_PROPERTY + ",\"direction\":{\"type\":\"string\",\"enum\":[\"up\",\"down\"]}", "\"component_id\",\"direction\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var component = requiredComponent(arguments);
        var direction = McpToolDescriptor.requiredText(arguments, "direction").toLowerCase();
        if (!direction.equals("up") && !direction.equals("down")) {
            throw new IllegalArgumentException("Argument 'direction' must be up or down");
        }
        presets.moveComponent(component.id(), direction.equals("up"));
        return "Moved %s '%s' %s.".formatted(component.kind(), component.name(), direction);
    }
}
