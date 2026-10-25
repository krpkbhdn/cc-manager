package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpCredentialReaderTest {

    @TempDir
    Path home;

    private final McpCredentialReader reader() {
        return new McpCredentialReader(new FakeHostEnvironment(HostEnvironment.OperatingSystem.WINDOWS, home));
    }

    private void write(String json) throws IOException {
        Files.createDirectories(home.resolve(".claude"));
        Files.writeString(home.resolve(".claude").resolve(".credentials.json"), json);
    }

    @Test
    void listsOnlyServersWithANonBlankToken() throws IOException {
        write("""
                {"claudeAiOauth":{"accessToken":"sk-ant-secret-1"},
                 "mcpOAuth":{"plugin:context7:context7|1c3f":{"serverName":"plugin:context7:context7","serverUrl":"https://c","accessToken":"tok-secret-2"},
                             "github|aa":{"serverName":"github","accessToken":""},
                             "sentry|bb":{"serverName":"sentry"},
                             "claude.ai Canva|cc":{"serverName":"claude.ai Canva","accessToken":"tok-secret-3"}}}
                """);

        var servers = reader().loggedInServers();

        assertThat(servers).containsExactlyInAnyOrder("plugin:context7:context7", "claude.ai Canva");
        assertThat(servers.toString()).doesNotContain("secret");
    }

    @Test
    void missingMalformedOrEmptyFilesMeanNobodyIsLoggedIn() throws IOException {
        assertThat(reader().loggedInServers()).isEmpty();
        write("{not json");
        assertThat(reader().loggedInServers()).isEmpty();
        write("{\"claudeAiOauth\":{}}");
        assertThat(reader().loggedInServers()).isEmpty();
        write("{\"mcpOAuth\":[1,\"x\"]}");
        assertThat(reader().loggedInServers()).isEmpty();
    }
}
