package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpLoginOutputTest {

    private static final String OUTPUT = """
            Starting authentication for "plugin:context7:context7"…
            [mcp-sdk] SEP-2352: something noisy
            If the browser didn't open, visit:
              https://clerk.context7.com/oauth/authorize?client_id=x&redirect_uri=http%3A%2F%2Flocalhost%3A44133%2Fcallback
            Waiting for authorization… (^C to cancel)
            """;

    @Test
    void findsTheSignInPageAfterTheBrowserHint() {
        assertThat(McpLoginOutput.signInUrl(OUTPUT)).contains("https://clerk.context7.com/oauth/authorize?client_id=x&redirect_uri=http%3A%2F%2Flocalhost%3A44133%2Fcallback");
        assertThat(McpLoginOutput.signInUrl("Starting authentication…")).isEmpty();
    }

    @Test
    void failureTextDropsNoiseWaitingPromptAndUrl() {
        var text = McpLoginOutput.failureText("Couldn't complete authentication: boom\n" + OUTPUT);

        assertThat(text).isEqualTo("Couldn't complete authentication: boom\nStarting authentication for \"plugin:context7:context7\"…");
    }

    @Test
    void theSignInPageEndsWhereTheWaitingPromptRunsIntoIt() {
        assertThat(McpLoginOutput.signInUrl("If the browser didn't open, visit:\n  https://a.test/x?y=1Waiting for authorization")).contains("https://a.test/x?y=1");
    }
}
