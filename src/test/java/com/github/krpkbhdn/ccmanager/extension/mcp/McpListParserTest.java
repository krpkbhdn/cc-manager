package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpListParserTest {

    @Test
    void parsesStatusesAndSkipsTheHeader() {
        var output = """
                Checking MCP server health…

                claude.ai Claude Docs: https://api.anthropic.com/v1/pages/mcp - ✔ Connected
                claude.ai Canva: https://mcp.canva.com/mcp - ! Needs authentication
                plugin:context7:context7: https://mcp.context7.com/mcp?x=1 (HTTP) - ! Needs authentication
                local: npx -y server --flag - ✘ Failed to connect
                slow: http://x - … Pending
                """;

        var servers = McpListParser.parse(output);

        assertThat(servers).extracting(McpHealth::name)
                .containsExactly("claude.ai Claude Docs", "claude.ai Canva", "plugin:context7:context7", "local", "slow");
        assertThat(servers).extracting(McpHealth::status).containsExactly(McpStatus.CONNECTED,
                McpStatus.NEEDS_AUTHENTICATION, McpStatus.NEEDS_AUTHENTICATION, McpStatus.FAILED, McpStatus.PENDING);
        assertThat(servers.get(3).target()).isEqualTo("npx -y server --flag");
        assertThat(servers.get(2).fromPlugin()).isTrue();
        assertThat(servers.get(2).target()).isEqualTo("https://mcp.context7.com/mcp?x=***");
        assertThat(servers.get(0).statusText()).isEqualTo("Connected");
    }

    @Test
    void unknownWordingIsUnknownNotAnError() {
        assertThat(McpListParser.parse("x: y - ? Whatever")).extracting(McpHealth::status).containsExactly(McpStatus.UNKNOWN);
    }
}
