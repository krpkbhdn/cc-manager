package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** What the Assistant may read of a component: MCP configs and settings never show values, instruction, skill and agent text only when one component is asked for. */
final class PresetComponentViews {

    private static final int PREVIEW_CHARS = 200;

    private PresetComponentViews() {}

    static Map<String, Object> summary(PresetComponent component) {
        var view = new LinkedHashMap<String, Object>();
        view.put("id", component.id());
        view.put("kind", component.kind());
        view.put("name", component.name());
        view.put("preview", preview(component));
        return view;
    }

    /** @return the summary plus the whole text of a kind that holds plain text; MCP servers and settings stay redacted, a file shows path and size only */
    static Map<String, Object> full(PresetComponent component) {
        var view = summary(component);
        var payload = component.payload();
        switch (component.kind()) {
            case INSTRUCTION -> view.put("markdown", payload.path("markdown").asString());
            case AGENT -> view.put("content", payload.path("content").asString());
            case SKILL -> {
                var files = new LinkedHashMap<String, String>();
                payload.path("files").properties().forEach(e -> files.put(e.getKey(), e.getValue().asString()));
                view.put("files", files);
            }
            default -> { }
        }
        return view;
    }

    private static String preview(PresetComponent component) {
        var payload = component.payload();
        return switch (component.kind()) {
            case MARKETPLACE -> McpTargetRedaction.redact(payload.path("source").asString());
            case PLUGIN -> "";
            case SKILL -> "files: " + String.join(", ", payload.path("files").propertyNames());
            case AGENT -> trimmed(payload.path("content").asString());
            case MCP_SERVER -> mcpServer(payload.path("config"));
            case INSTRUCTION -> trimmed(payload.path("markdown").asString());
            case SETTINGS -> "keys: " + String.join(", ", payload.path("fragment").propertyNames()) + " (values hidden)";
            case FILE -> payload.path("path").asString() + " (" + payload.path("content").asString().length() + " characters)";
        };
    }

    private static String mcpServer(JsonNode config) {
        if (!config.isObject()) {
            return McpTargetRedaction.redact(config.asString());
        }
        var target = config.hasNonNull("url") ? config.path("url").asString() : config.path("command").asString();
        return McpTargetRedaction.redact(target) + " (keys: " + String.join(", ", config.propertyNames()) + "; args, env and headers hidden)";
    }

    private static String trimmed(String text) {
        var flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= PREVIEW_CHARS ? flat : flat.substring(0, PREVIEW_CHARS) + "...";
    }
}
