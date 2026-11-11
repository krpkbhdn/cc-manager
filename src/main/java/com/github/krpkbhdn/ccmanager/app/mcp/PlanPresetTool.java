package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class PlanPresetTool extends PresetTool {

    static final String PROJECT_ID_PROPERTY = "\"project_id\":{\"type\":\"integer\",\"description\":\"Project id from list_projects\"}";

    private final PresetApplyService apply;
    private final ProjectService projects;

    PlanPresetTool(ProjectPresetService presets, PresetApplyService apply, ProjectService projects) {
        super(presets);
        this.apply = apply;
        this.projects = projects;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "plan_preset";
    }

    @Override
    public String description() {
        return "Shows what applying a preset to a project would do, one line per component (action, kind, title, detail). Reads the project, writes nothing.";
    }

    @Override
    public String inputSchema() {
        return schema(PROJECT_ID_PROPERTY + "," + PRESET_ID_PROPERTY, "\"project_id\",\"preset_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var project = projects.find(ToolArguments.requiredId(arguments, "project_id"))
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + arguments.path("project_id").asString()));
        var preset = requiredPreset(arguments);
        var lines = apply.plan(preset.id(), project.path());
        return lines.isEmpty() ? "Preset " + describe(preset) + " has no components."
                : lines.stream().map(l -> "%s %s '%s' (component %d): %s".formatted(l.action(), l.kind(), l.title(), l.componentId(), l.detail())).collect(Collectors.joining("\n"));
    }
}
