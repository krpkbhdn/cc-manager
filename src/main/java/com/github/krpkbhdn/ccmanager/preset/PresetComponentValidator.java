package com.github.krpkbhdn.ccmanager.preset;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks and normalises component payloads. Paths come from user input and end up written into a project folder,
 * so they must be relative, normalised and stay inside it.
 */
final class PresetComponentValidator {

    static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern PLUGIN = Pattern.compile("[^@\\s]+@[^@\\s]+");
    private static final Pattern FOLDER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /** Same limits as the skill import in the UI (SkillImportLimits). */
    static final int MAX_SKILL_FILES = 40;
    static final int MAX_PAYLOAD_BYTES = 400_000;

    private PresetComponentValidator() {
    }

    /** @return the spec with a trimmed name and a normalised payload
     *  @throws IllegalArgumentException with a user-readable message */
    static PresetComponentSpec validate(PresetComponentKind kind, String name, JsonNode payload) {
        if (kind == null) {
            throw new IllegalArgumentException("The component needs a kind.");
        }
        var cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("The component name must not be empty.");
        }
        var body = payload == null || payload.isNull() || payload.isMissingNode() ? MAPPER.createObjectNode() : payload;
        var normalised = switch (kind) {
            case MARKETPLACE -> requireText(body, "source", "A marketplace needs a source.");
            case PLUGIN -> plugin(cleanName);
            case SKILL -> skill(cleanName, body);
            case AGENT -> agent(cleanName, body);
            case MCP_SERVER -> mcpServer(body);
            case INSTRUCTION -> instruction(cleanName, body);
            case SETTINGS -> settings(body);
            case FILE -> file(body);
        };
        if (normalised.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("The component content is larger than " + MAX_PAYLOAD_BYTES / 1000 + " KB.");
        }
        return new PresetComponentSpec(kind, cleanName, normalised);
    }

    /** @return the path with forward slashes and no dot segments
     *  @throws IllegalArgumentException when it is empty, absolute, has a drive or leaves the project */
    static String safeRelativePath(String path) {
        var slashed = path == null ? "" : path.strip().replace('\\', '/');
        if (slashed.isEmpty() || slashed.startsWith("/") || slashed.contains(":")) {
            throw new IllegalArgumentException("The path must be relative to the project: " + path);
        }
        var segments = new ArrayList<String>();
        for (var segment : slashed.split("/")) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("The path must stay inside the project: " + path);
            }
            if (!segment.isEmpty() && !segment.equals(".")) {
                ProjectPathRules.requireSafeSegment(segment, path);
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("The path must name a file: " + path);
        }
        return String.join("/", segments);
    }

    private static ObjectNode requireText(JsonNode body, String field, String message) {
        requireObject(body);
        if (!body.path(field).isString() || body.path(field).asString().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return MAPPER.createObjectNode().put(field, body.path(field).asString());
    }

    private static ObjectNode instruction(String name, JsonNode body) {
        var result = requireText(body, "markdown", "An instruction needs text.");
        var text = name + "\n" + result.path("markdown").asString();
        if (text.contains("cc-manager:begin") || text.contains("cc-manager:end")) {
            throw new IllegalArgumentException("An instruction must not contain the managed block markers (cc-manager:begin / cc-manager:end).");
        }
        return result;
    }

    private static ObjectNode plugin(String name) {
        if (!PLUGIN.matcher(name).matches()) {
            throw new IllegalArgumentException("A plugin is named plugin@marketplace: " + name);
        }
        return MAPPER.createObjectNode();
    }

    private static ObjectNode skill(String name, JsonNode body) {
        requireFolderName(name);
        requireObject(body);
        var files = body.path("files");
        if (!files.isObject() || files.isEmpty()) {
            throw new IllegalArgumentException("A skill needs at least one file.");
        }
        if (files.size() > MAX_SKILL_FILES) {
            throw new IllegalArgumentException("A skill can have at most " + MAX_SKILL_FILES + " files, this one has " + files.size() + ".");
        }
        var normalised = MAPPER.createObjectNode();
        for (var entry : files.properties()) {
            if (!entry.getValue().isString()) {
                throw new IllegalArgumentException("A skill file must be text: " + entry.getKey());
            }
            var path = safeRelativePath(entry.getKey());
            if (normalised.has(path)) {
                throw new IllegalArgumentException("Two skill files point to " + path);
            }
            normalised.put(path, entry.getValue().asString());
        }
        return MAPPER.createObjectNode().set("files", normalised);
    }

    private static ObjectNode agent(String name, JsonNode body) {
        requireFolderName(name);
        return requireText(body, "content", "An agent needs its definition.");
    }

    private static ObjectNode mcpServer(JsonNode body) {
        requireObject(body);
        var config = body.path("config");
        var empty = config.isMissingNode() || config.isNull() || (config.isString() && config.asString().isBlank()) || (config.isObject() && config.isEmpty());
        if (empty) {
            throw new IllegalArgumentException("An MCP server needs a configuration.");
        }
        return MAPPER.createObjectNode().set("config", config);
    }

    private static ObjectNode settings(JsonNode body) {
        requireObject(body);
        if (!body.path("fragment").isObject()) {
            throw new IllegalArgumentException("The settings fragment must be a JSON object.");
        }
        return MAPPER.createObjectNode().set("fragment", body.path("fragment"));
    }

    private static ObjectNode file(JsonNode body) {
        requireObject(body);
        if (!body.path("content").isString()) {
            throw new IllegalArgumentException("A file needs content.");
        }
        var result = MAPPER.createObjectNode().put("path", safeRelativePath(body.path("path").asString("")));
        result.put("content", body.path("content").asString());
        var lines = result.putArray("gitignore");
        for (var line : body.path("gitignore")) {
            if (!line.isString() || line.asString().isBlank()) {
                throw new IllegalArgumentException("A gitignore line must be non-empty text.");
            }
            lines.add(line.asString().strip());
        }
        return result;
    }

    private static void requireObject(JsonNode body) {
        if (!body.isObject()) {
            throw new IllegalArgumentException("The component content must be a JSON object.");
        }
    }

    private static void requireFolderName(String name) {
        if (!FOLDER_NAME.matcher(name).matches() || name.contains("..")) {
            throw new IllegalArgumentException("Use letters, digits, '.', '_' or '-' in the name: " + name);
        }
        ProjectPathRules.requireSafeSegment(name, name);
    }
}
