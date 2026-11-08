package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class RestorePresetTool extends PresetTool {

    RestorePresetTool(ProjectPresetService presets) {
        super(presets);
    }

    @Override
    public String name() {
        return "restore_preset";
    }

    @Override
    public String description() {
        return "Resets a bundled preset to its shipped content (also brings back a deleted one); without 'key' restores all bundled presets. Edits made to them are lost.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"string\",\"description\":\"Bundled key from list_presets, omit for all\"}}}";
    }

    @Override
    public String call(JsonNode arguments) {
        if (!arguments.hasNonNull("key")) {
            presets.restoreAll();
            return "All bundled presets restored.";
        }
        var key = arguments.path("key").asString().strip();
        presets.restore(key);
        return "Bundled preset '" + key + "' restored.";
    }
}
