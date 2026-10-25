package com.github.krpkbhdn.ccmanager.extension.mcp;

/** One line of {@code claude mcp list}; plugin servers are named {@code plugin:<plugin>:<server>}. */
public record McpHealth(String name, String target, McpStatus status, String statusText) {

    public boolean fromPlugin() {
        return name.startsWith("plugin:");
    }
}
