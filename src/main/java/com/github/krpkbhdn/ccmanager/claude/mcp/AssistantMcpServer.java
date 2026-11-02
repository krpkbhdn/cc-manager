package com.github.krpkbhdn.ccmanager.claude.mcp;

import java.util.List;
import java.util.Optional;

/** The app MCP server of the Assistant as the hook server and launcher see it, so neither depends on the app control package. */
public interface AssistantMcpServer {

    String SERVER_NAME = "cc-manager-app";

    /** How Claude Code and the proposal dialog name the tools of this server. */
    String TOOL_NAME_PREFIX = "mcp__" + SERVER_NAME + "__";

    /** @return the JSON response, empty for a notification */
    Optional<String> handle(byte[] body);

    /** @return the sorted names of all tools; the endpoint's own dialog is the only gate, so Claude pre-allows every one (a second Claude prompt would ask twice) */
    List<String> toolNames();
}
