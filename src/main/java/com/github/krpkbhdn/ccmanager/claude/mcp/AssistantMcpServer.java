package com.github.krpkbhdn.ccmanager.claude.mcp;

import java.util.List;
import java.util.Optional;

/** The app MCP server of the Assistant as the hook server and launcher see it, so neither depends on the app control package. */
public interface AssistantMcpServer {

    String SERVER_NAME = "cc-manager-app";

    /** @return the JSON response, empty for a notification */
    Optional<String> handle(byte[] body);

    /** @return the sorted names of the tools the Assistant may call without a permission dialog */
    List<String> autoApprovedToolNames();
}
