package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import tools.jackson.databind.JsonNode;

/** One tool of the {@code cc-manager-app} MCP server that controls the app; deliberately not a McpTool, so project sessions can never get it. */
public interface AssistantMcpTool extends McpToolDescriptor {

    /**
     * @return text for Claude
     * @throws IllegalArgumentException or IllegalStateException with a message Claude can act on
     */
    String call(JsonNode arguments);

    /** @return true when the Assistant may call the tool without a permission dialog (read-only or easily undone) */
    default boolean autoApproved() {
        return false;
    }
}
