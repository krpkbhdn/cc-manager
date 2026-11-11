package com.github.krpkbhdn.ccmanager.claude.mcp;

import tools.jackson.databind.JsonNode;

/** One tool cc-manager offers to the Claude sessions it launches, scoped to the project of the calling session. */
public interface McpTool extends McpToolDescriptor {

    /**
     * @return text for Claude
     * @throws IllegalArgumentException or IllegalStateException with a message Claude can act on
     */
    String call(long projectId, JsonNode arguments);

    /** Variant for tools that need the calling session; the endpoint always uses this one. */
    default String call(long projectId, String ccmSessionId, JsonNode arguments) {
        return call(projectId, arguments);
    }
}
