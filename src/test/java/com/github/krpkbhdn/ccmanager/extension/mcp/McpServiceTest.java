package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionProperties;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.RecordingCli;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpServiceTest {

    private static final Path PROJECT = Path.of("/work/project");
    private final RecordingCli cli = new RecordingCli();
    private final McpService service =
            new McpService(cli, mock(McpConfigReader.class), mock(McpCredentialReader.class), new ExtensionProperties(30, 180, 90, 300));

    @Test
    void addPassesTheJsonAsOneArgumentInTheProjectDirectory() {
        var json = "{\"type\":\"http\",\"url\":\"https://x\"}";

        service.add("my-server", json, ExtensionScope.LOCAL, PROJECT);

        assertThat(cli.lastCall()).containsExactly("mcp", "add-json", "--scope", "local", "my-server", json);
        assertThat(cli.directories).containsExactly(PROJECT);
    }

    @Test
    void addRejectsNamesThatCouldBeTakenForFlagsAndNonObjectJson() {
        assertThatThrownBy(() -> service.add("--evil", "{}", ExtensionScope.USER, null)).isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> service.add("ok", "[1]", ExtensionScope.USER, null)).isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> service.add("ok", "not json", ExtensionScope.USER, null)).isInstanceOf(ExtensionException.class);
        assertThat(cli.calls).isEmpty();
    }

    @Test
    void failedAddRedactsSingleEnvAndHeaderValuesEchoedAlone() {
        var json = "{\"command\":\"x\",\"env\":{\"TOKEN\":\"SECRET1\"},\"headers\":{\"Authorization\":\"Bearer SECRET2\"}}";
        cli.answering(1, "", "bad env TOKEN=SECRET1 header Bearer SECRET2");

        assertThatThrownBy(() -> service.add("s", json, ExtensionScope.USER, null))
                .isInstanceOfSatisfying(ExtensionException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain("SECRET1", "SECRET2");
                    assertThat(e.output()).doesNotContain("SECRET1", "SECRET2");
                });
    }

    @Test
    void failedAddNeverEchoesTheConfigJson() {
        var json = "{\"command\":\"x\",\"env\":{\"TOKEN\":\"SECRET\"}}";
        cli.answering(1, "", "Invalid config " + json);

        assertThatThrownBy(() -> service.add("s", json, ExtensionScope.USER, null))
                .isInstanceOfSatisfying(ExtensionException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain("SECRET").contains("<config>");
                    assertThat(e.output()).doesNotContain("SECRET");
                });
    }

    @Test
    void loginUsesTheProjectDirectoryAndRejectsFlagLikeNames() {
        service.login("claude.ai Canva", PROJECT, url -> {});
        service.logout("plugin:context7:context7", PROJECT);

        assertThat(cli.calls).containsExactly(List.of("mcp", "login", "claude.ai Canva"), List.of("mcp", "logout", "plugin:context7:context7"));
        assertThat(cli.directories).containsExactly(PROJECT, PROJECT);
        assertThatThrownBy(() -> service.login("--no-browser", PROJECT, url -> {})).isInstanceOf(ExtensionException.class);
    }

    @Test
    void loginRunsInATerminalReportsTheSignInPageOnceAndExplainsFailures() {
        var urls = new java.util.ArrayList<String>();
        cli.answering(1, "Couldn't complete authentication: boom\n[mcp-sdk] noise\nIf the browser didn't open, visit:\n  https://x.test/auth?s=1\nWaiting for authorization", "");

        assertThatThrownBy(() -> service.login("s", PROJECT, urls::add))
                .isInstanceOf(ExtensionException.class)
                .hasMessage("claude mcp login failed: Couldn't complete authentication: boom");
        assertThat(cli.terminalCalls).containsExactly(List.of("mcp", "login", "s"));
        assertThat(urls).containsExactly("https://x.test/auth?s=1");
    }

    @Test
    void removeUsesTheScope() {
        service.remove("s", ExtensionScope.USER, null);

        assertThat(cli.lastCall()).containsExactly("mcp", "remove", "--scope", "user", "s");
    }

    @Test
    void healthParsesTheListOutput() {
        cli.answering("Checking MCP server health…\n\na: http://x - ✔ Connected\n");

        assertThat(service.health(PROJECT)).extracting(McpHealth::status).containsExactly(McpStatus.CONNECTED);
        assertThat(cli.directories).containsExactly(PROJECT);
    }

    @Test
    void cliFailureKeepsTheMessage() {
        cli.answering(1, "", "No MCP server found with name: zz");

        assertThatThrownBy(() -> service.remove("zz", ExtensionScope.USER, null))
                .hasMessageContaining("No MCP server found with name: zz");
    }
}
