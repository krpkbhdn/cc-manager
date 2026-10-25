package com.github.krpkbhdn.ccmanager.extension.mcp;

/** One line of {@code claude mcp list}; plugin servers are named {@code plugin:<plugin>:<server>}, claude.ai connectors {@code claude.ai <Name>}. */
public record McpHealth(String name, String target, McpStatus status, String statusText) {

    public static final String CONNECTOR_PREFIX = "claude.ai ";

    public boolean fromConnector() {
        return name.startsWith(CONNECTOR_PREFIX);
    }

    public boolean fromPlugin() {
        return name.startsWith("plugin:");
    }
}
