package com.github.krpkbhdn.ccmanager.extension.mcp;

/** Whether the user trusted a server declared in a project's .mcp.json; other scopes need no approval. */
public enum McpApproval {
    NOT_REQUIRED, APPROVED, DISABLED, PENDING
}
