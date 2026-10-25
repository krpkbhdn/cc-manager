package com.github.krpkbhdn.ccmanager.extension.mcp;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;

/** A configured MCP server without its secrets (env, headers); target is the command or URL. */
public record McpServerConfig(String name, ExtensionScope scope, String transport, String target, McpApproval approval) {}
