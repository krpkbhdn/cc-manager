package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpTargetRedactionTest {

    @Test
    void removesUserInfoPathAndQueryFromUrl() {
        var result = McpTargetRedaction.redact("https://user:pw-secret@example.com:8443/mcp?token=abc123&k=v#frag");

        assertThat(result).isEqualTo("https://example.com:8443/…").doesNotContain("pw-secret", "abc123", "user");
    }

    @Test
    void tokenInPathIsHidden() {
        assertThat(McpTargetRedaction.redact("https://hooks.example.com/t/SECRET99/mcp")).isEqualTo("https://hooks.example.com/…");
    }

    @Test
    void stdioShowsExecutableOnly() {
        var result = McpTargetRedaction.redact("npx -y @acme/server --api-key sk-12345");

        assertThat(result).isEqualTo("npx").doesNotContain("sk-12345");
    }

    @Test
    void commandWithUrlArgumentShowsExecutableOnly() {
        var result = McpTargetRedaction.redact("npx mcp-remote --header Authorization:Bearer SECRET https://x");

        assertThat(result).isEqualTo("npx");
    }

    @Test
    void malformedUrlIsNotEchoed() {
        var result = McpTargetRedaction.redact("https://exa mple.com/x?token=abc123");

        assertThat(result).doesNotContain("abc123");
    }

    @Test
    void hostlessUrlIsNotEchoed() {
        assertThat(McpTargetRedaction.redact("https:///SECRET/x")).doesNotContain("SECRET");
    }

    @Test
    void nullIsEmpty() {
        assertThat(McpTargetRedaction.redact(null)).isEmpty();
    }
}
