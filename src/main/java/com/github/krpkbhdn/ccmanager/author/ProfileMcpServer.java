package com.github.krpkbhdn.ccmanager.author;

import tools.jackson.databind.JsonNode;

/** An MCP server to register; {@code config} is the JSON passed to {@code claude mcp add-json}. */
public record ProfileMcpServer(String name, JsonNode config) {}
