package com.github.krpkbhdn.ccmanager.extension;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Timeouts of the claude plugin/mcp commands: local queries, commands that download, and the MCP health check. */
@ConfigurationProperties("cc-manager.extension")
public record ExtensionProperties(
        @DefaultValue("30") int queryTimeoutSeconds,
        @DefaultValue("180") int networkTimeoutSeconds,
        @DefaultValue("90") int mcpHealthTimeoutSeconds) {

    public Duration queryTimeout() {
        return Duration.ofSeconds(queryTimeoutSeconds);
    }

    public Duration networkTimeout() {
        return Duration.ofSeconds(networkTimeoutSeconds);
    }

    public Duration mcpHealthTimeout() {
        return Duration.ofSeconds(mcpHealthTimeoutSeconds);
    }
}
