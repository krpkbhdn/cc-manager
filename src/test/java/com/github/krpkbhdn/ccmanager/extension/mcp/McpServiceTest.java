package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionProperties;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.RecordingCli;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class McpServiceTest {

    private static final Path PROJECT = Path.of("/work/project");
    private final RecordingCli cli = new RecordingCli();
    private final McpService service =
            new McpService(cli, mock(McpConfigReader.class), new ExtensionProperties(30, 180, 90));

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
