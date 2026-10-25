package com.github.krpkbhdn.ccmanager.claude.mcp;

import tools.jackson.databind.JsonNode;

/** One tool cc-manager offers to the Claude sessions it launches, scoped to the project of the calling session. */
public interface McpTool {

    String name();

    String description();

    /** JSON schema of the arguments object, as JSON text. */
    String inputSchema();

    /**
     * @return text for Claude
     * @throws IllegalArgumentException or IllegalStateException with a message Claude can act on
     */
    String call(long projectId, JsonNode arguments);

    static String requiredText(JsonNode arguments, String field) {
        var value = arguments.path(field).asString("").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Argument '" + field + "' is required");
        }
        return value;
    }
}
