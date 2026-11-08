package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ApplyPresetTool extends PresetTool {

    private final PresetApplyService apply;
    private final ProjectService projects;

    ApplyPresetTool(ProjectPresetService presets, PresetApplyService apply, ProjectService projects) {
        super(presets);
        this.apply = apply;
        this.projects = projects;
    }

    @Override
    public String name() {
        return "apply_preset";
    }

    @Override
    public String description() {
        return "Writes a preset into a project folder (skills, agents, files, CLAUDE.md blocks, settings, MCP servers, plugins); all components unless component_ids is given. Run plan_preset first to see the effect.";
    }

    @Override
    public String inputSchema() {
        return schema(PlanPresetTool.PROJECT_ID_PROPERTY + "," + PRESET_ID_PROPERTY
                + ",\"component_ids\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"Optional subset of the preset's components\"}", "\"project_id\",\"preset_id\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "project_id");
        var project = projects.find(id).orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        var preset = requiredPreset(arguments);
        var selected = selection(arguments, preset.components());
        var result = apply.apply(project.id(), preset.id(), project.path(), selected);
        var lines = result.outcomes().stream().map(o -> "%s %s '%s': %s".formatted(o.status(), o.kind(), o.title(), o.message())).collect(Collectors.joining("\n"));
        return "Preset %s on project '%s'%s:\n%s".formatted(describe(preset), project.name(), result.recorded() ? " (recorded as applied)" : "", lines);
    }

    private static Set<Long> selection(JsonNode arguments, java.util.List<PresetComponent> components) {
        var all = components.stream().map(PresetComponent::id).collect(Collectors.toSet());
        if (!arguments.hasNonNull("component_ids")) {
            return all;
        }
        var chosen = new HashSet<Long>();
        arguments.path("component_ids").forEach(node -> chosen.add(node.asLong()));
        if (!all.containsAll(chosen)) {
            throw new IllegalArgumentException("component_ids must belong to the preset: " + all);
        }
        return chosen;
    }
}
