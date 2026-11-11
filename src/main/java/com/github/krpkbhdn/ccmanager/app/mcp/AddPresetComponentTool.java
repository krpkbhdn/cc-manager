package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class AddPresetComponentTool extends PresetTool {

    private final PluginService plugins;

    AddPresetComponentTool(ProjectPresetService presets, PluginService plugins) {
        super(presets);
        this.plugins = plugins;
    }

    @Override
    public String name() {
        return "add_preset_component";
    }

    @Override
    public String description() {
        return "Adds a component to a preset. kind: MARKETPLACE (name, source), PLUGIN (name = plugin@marketplace; its marketplace component is added too when 'source' is given or the marketplace is known), "
                + "SKILL (name = folder, 'files' or 'skill_markdown' + 'description'), AGENT (name, content), MCP_SERVER (name, config), INSTRUCTION (name = title, markdown), SETTINGS (name, fragment; one per preset), FILE (name, path, content, gitignore).";
    }

    @Override
    public String inputSchema() {
        var kinds = String.join("\",\"", Arrays.stream(PresetComponentKind.values()).map(Enum::name).toList());
        return schema(PRESET_ID_PROPERTY + ",\"kind\":{\"type\":\"string\",\"enum\":[\"" + kinds + "\"]},\"name\":{\"type\":\"string\"}," + PresetPayloads.PROPERTIES,
                "\"preset_id\",\"kind\",\"name\"");
    }

    @Override
    public String call(JsonNode arguments) {
        var preset = requiredPreset(arguments);
        var kind = kind(arguments);
        var name = McpToolDescriptor.requiredText(arguments, "name");
        var marketplace = kind == PresetComponentKind.PLUGIN ? marketplaceToAdd(preset, name, arguments) : Optional.<NewMarketplace>empty();
        var added = presets.addComponent(preset.id(), kind, name, PresetPayloads.build(kind, name, arguments, null));
        var message = "Added %s '%s' (component %d) to preset %s.".formatted(kind, name, added.id(), describe(preset));
        if (marketplace.isEmpty()) {
            return message;
        }
        var payload = PresetPayloads.build(PresetComponentKind.MARKETPLACE, marketplace.get().name(), arguments, null).put("source", marketplace.get().source());
        presets.addComponent(preset.id(), PresetComponentKind.MARKETPLACE, marketplace.get().name(), payload);
        return message + " Also added marketplace '" + marketplace.get().name() + "'.";
    }

    private record NewMarketplace(String name, String source) {}

    /** Resolved before anything is saved, so a plugin is never stored without a marketplace that can be applied. */
    private Optional<NewMarketplace> marketplaceToAdd(ProjectPreset preset, String plugin, JsonNode arguments) {
        var marketplace = plugin.substring(plugin.indexOf('@') + 1);
        var listed = presets.get(preset.id()).orElseThrow().components().stream()
                .anyMatch(c -> c.kind() == PresetComponentKind.MARKETPLACE && c.name().equals(marketplace));
        if (listed) {
            return Optional.empty();
        }
        var source = sourceOf(marketplace, arguments)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace '" + marketplace + "' is unknown: pass its source."));
        return Optional.of(new NewMarketplace(marketplace, source));
    }

    private Optional<String> sourceOf(String marketplace, JsonNode arguments) {
        var given = arguments.path("source").asString("").strip();
        if (!given.isEmpty()) {
            return Optional.of(given);
        }
        return plugins.marketplaces().stream().filter(m -> m.name().equals(marketplace)).findFirst().map(Marketplace::reference);
    }

    static PresetComponentKind kind(JsonNode arguments) {
        var text = McpToolDescriptor.requiredText(arguments, "kind");
        try {
            return PresetComponentKind.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown kind '" + text + "', use one of " + Arrays.toString(PresetComponentKind.values()));
        }
    }
}
