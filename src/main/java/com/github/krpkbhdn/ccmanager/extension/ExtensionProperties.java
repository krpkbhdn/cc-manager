package com.github.krpkbhdn.ccmanager.extension;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Timeouts of the claude plugin/mcp commands: local queries, commands that download, the MCP health check and the wait for a browser sign-in. */
@ConfigurationProperties("cc-manager.extension")
public record ExtensionProperties(
        @DefaultValue("30") int queryTimeoutSeconds,
        @DefaultValue("180") int networkTimeoutSeconds,
        @DefaultValue("90") int mcpHealthTimeoutSeconds,
        @DefaultValue("300") int mcpLoginTimeoutSeconds) {

    public Duration queryTimeout() {
        return Duration.ofSeconds(queryTimeoutSeconds);
    }

    public Duration networkTimeout() {
        return Duration.ofSeconds(networkTimeoutSeconds);
    }

    public Duration mcpHealthTimeout() {
        return Duration.ofSeconds(mcpHealthTimeoutSeconds);
    }

    public Duration mcpLoginTimeout() {
        return Duration.ofSeconds(mcpLoginTimeoutSeconds);
    }
}
