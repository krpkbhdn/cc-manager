package com.github.krpkbhdn.ccmanager.claude.mcp;

import tools.jackson.databind.JsonNode;

/** What every MCP tool tells a client in {@code tools/list}; the two tool families add their own call signature. */
public interface McpToolDescriptor {

    String name();

    String description();

    /** JSON schema of the arguments object, as JSON text. */
    String inputSchema();

    static String requiredText(JsonNode arguments, String field) {
        var value = arguments.path(field).asString("").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Argument '" + field + "' is required");
        }
        return value;
    }
}
