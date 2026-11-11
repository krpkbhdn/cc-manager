package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import com.github.krpkbhdn.ccmanager.preset.apply.CodeExecutingPaths;
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
class ApplyPresetTool extends PresetTool {

    private static final int MAX_PLAN_CHARS = 100_000;
    private static final String MASKED = "<hidden>";

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

    /**
     * The dialog shows the plan the UI review shows (selected components only): every header and WARNING first, then the bodies.
     * Bodies are never cut, since the apply writes them whole; a plan over the cap is refused so no one allows what they could not read.
     */
    @Override
    public JsonNode approvalInput(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "project_id");
        var project = projects.find(id).orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        var preset = requiredPreset(arguments);
        var selected = selection(arguments, preset.components());
        var components = preset.components().stream().collect(Collectors.toMap(PresetComponent::id, c -> c));
        var headers = new StringBuilder();
        var bodies = new StringBuilder();
        for (var change : apply.plan(preset.id(), project.path())) {
            if (!selected.contains(change.componentId())) {
                continue;
            }
            var component = components.get(change.componentId());
            headers.append("%s %s '%s': %s%n".formatted(change.action(), change.kind(), change.title(), change.detail()));
            executableKeys(component).ifPresent(keys ->
                    headers.append("  WARNING: settings that run code or change permissions: ").append(keys).append('\n'));
            var body = component.kind() == PresetComponentKind.SETTINGS ? settingsBody(component) : change.preview();
            if (body == null) {
                continue;
            }
            var heading = "--- %s '%s'%n".formatted(change.kind(), change.title());
            bodies.append(heading).append(body).append('\n');
        }
        var text = headers + (bodies.isEmpty() ? "" : "\n" + bodies);
        if (text.length() > MAX_PLAN_CHARS) {
            throw new IllegalArgumentException("The plan is too large to show in full for approval here (%d characters, limit %d). Nothing was written. Ask the user to apply this preset from the app UI with 'Apply to project...'."
                    .formatted(text.length(), MAX_PLAN_CHARS));
        }
        var input = ((ObjectNode) arguments.deepCopy()).put("will_write_to", project.path().toString());
        return input.put("plan", headers.isEmpty() ? "Nothing to write" : text);
    }

    /** The fragment as it will be merged, with env values masked since they usually hold tokens. */
    private static String settingsBody(PresetComponent component) {
        var fragment = component.payload().path("fragment");
        if (!fragment.isObject()) {
            return null;
        }
        var copy = (ObjectNode) fragment.deepCopy();
        if (copy.path("env").isObject()) {
            var env = (ObjectNode) copy.path("env");
            env.propertyNames().forEach(key -> env.put(key, MASKED));
        }
        return copy.toPrettyString();
    }

    private static Optional<String> executableKeys(PresetComponent component) {
        if (component.kind() != PresetComponentKind.SETTINGS) {
            return Optional.empty();
        }
        var found = CodeExecutingPaths.executableSettingsKeys(component.payload().path("fragment"));
        return found.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", found));
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
