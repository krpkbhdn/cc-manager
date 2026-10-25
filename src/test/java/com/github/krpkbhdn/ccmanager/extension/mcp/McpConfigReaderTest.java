package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpConfigReaderTest {

    @TempDir
    Path home;

    @TempDir
    Path project;

    private McpConfigReader reader(HostEnvironment.OperatingSystem os) {
        return new McpConfigReader(new FakeHostEnvironment(os, home));
    }

    @Test
    void readsUserLocalAndProjectServersWithoutSecrets() throws IOException {
        var key = project.toString().replace('/', '\\').toUpperCase();
        Files.writeString(home.resolve(".claude.json"), """
                {"mcpServers":{"u":{"type":"http","url":"https://u","headers":{"Authorization":"secret"}}},
                 "projects":{"%s":{"mcpServers":{"l":{"command":"npx","args":["x"],"env":{"TOKEN":"secret"}}},
                                   "enabledMcpjsonServers":["ok"],"disabledMcpjsonServers":["off"]},
                             "C:\\\\other":{"mcpServers":{"nope":{"command":"x"}}}}}
                """.formatted(key.replace("\\", "\\\\")));
        Files.writeString(project.resolve(".mcp.json"),
                "{\"mcpServers\":{\"ok\":{\"command\":\"a\"},\"off\":{\"url\":\"https://b\"},\"new\":{\"command\":\"c\"}}}");

        var servers = reader(HostEnvironment.OperatingSystem.WINDOWS).read(project);

        assertThat(servers).containsExactly(
                new McpServerConfig("u", ExtensionScope.USER, "http", "https://u", McpApproval.NOT_REQUIRED),
                new McpServerConfig("l", ExtensionScope.LOCAL, "stdio", "npx", McpApproval.NOT_REQUIRED),
                new McpServerConfig("ok", ExtensionScope.PROJECT, "stdio", "a", McpApproval.APPROVED),
                new McpServerConfig("off", ExtensionScope.PROJECT, "stdio", "https://b", McpApproval.DISABLED),
                new McpServerConfig("new", ExtensionScope.PROJECT, "stdio", "c", McpApproval.PENDING));
        assertThat(servers.toString()).doesNotContain("secret");
    }

    @Test
    void urlTargetsHideQueryValuesAndCredentials() throws IOException {
        Files.writeString(home.resolve(".claude.json"), """
                {"mcpServers":{"u":{"type":"http","url":"https://user:pw@host/mcp?api_key=SECRET&mode=a#f"}}}""");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(null)).extracting(McpServerConfig::target)
                .containsExactly("https://***@host/mcp?api_key=***&mode=***#f");
    }

    @Test
    void missingFilesMeanNoServers() {
        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(project)).isEmpty();
    }

    @Test
    void withoutProjectOnlyUserServersAreRead() throws IOException {
        Files.writeString(home.resolve(".claude.json"), "{\"mcpServers\":{\"u\":{\"command\":\"x\"}}}");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(null)).extracting(McpServerConfig::name).containsExactly("u");
    }
}
