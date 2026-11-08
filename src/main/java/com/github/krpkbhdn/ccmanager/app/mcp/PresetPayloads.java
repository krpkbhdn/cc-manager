package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Turns the typed arguments of the component tools into the JSON payload of a kind; the backend validator rejects what is wrong. */
final class PresetPayloads {

    static final String PROPERTIES = "\"markdown\":{\"type\":\"string\",\"description\":\"INSTRUCTION: the text\"},"
            + "\"content\":{\"type\":\"string\",\"description\":\"AGENT: the definition; FILE: the file text\"},"
            + "\"skill_markdown\":{\"type\":\"string\",\"description\":\"SKILL shorthand: body of SKILL.md, frontmatter name and description are added (needs description)\"},"
            + "\"description\":{\"type\":\"string\",\"description\":\"SKILL shorthand: when to use the skill\"},"
            + "\"files\":{\"type\":\"object\",\"description\":\"SKILL: relative path to text\",\"additionalProperties\":{\"type\":\"string\"}},"
            + "\"path\":{\"type\":\"string\",\"description\":\"FILE: path relative to the project\"},"
            + "\"gitignore\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"FILE: lines added to .gitignore\"},"
            + "\"config\":{\"type\":\"object\",\"description\":\"MCP_SERVER: the server configuration\"},"
            + "\"fragment\":{\"type\":\"object\",\"description\":\"SETTINGS: JSON merged into .claude/settings.json\"},"
            + "\"source\":{\"type\":\"string\",\"description\":\"MARKETPLACE: owner/repo, git URL or path; PLUGIN: optional source of its marketplace\"}";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String SKILL_FILE = "SKILL.md";
    private static final String[] COPIED = {"markdown", "content", "files", "path", "gitignore", "config", "fragment", "source"};

    private PresetPayloads() {}

    /** @param current payload to change field by field, or null to start empty */
    static ObjectNode build(PresetComponentKind kind, String name, JsonNode arguments, JsonNode current) {
        var payload = current instanceof ObjectNode existing ? existing.deepCopy() : NODES.objectNode();
        for (var field : COPIED) {
            if (arguments.hasNonNull(field)) {
                payload.set(field, arguments.get(field));
            }
        }
        if (kind == PresetComponentKind.SKILL && arguments.hasNonNull("skill_markdown")) {
            var files = payload.path("files") instanceof ObjectNode existing ? existing : NODES.objectNode();
            files.put(SKILL_FILE, skillMarkdown(name, arguments));
            payload.set("files", files);
        }
        return payload;
    }

    private static String skillMarkdown(String name, JsonNode arguments) {
        var description = arguments.path("description").asString("").strip();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("Argument 'description' is required with 'skill_markdown'");
        }
        return "---\nname: " + name + "\ndescription: " + description.replace('\n', ' ') + "\n---\n\n" + arguments.path("skill_markdown").asString();
    }
}
