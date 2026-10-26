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
    void projectSettingsFilesApproveAndDisableMcpjsonServers() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".mcp.json"), "{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"x\"},\"c\":{\"command\":\"x\"}}}");
        Files.writeString(project.resolve(".claude/settings.json"), "{\"enabledMcpjsonServers\":[\"a\"],\"disabledMcpjsonServers\":[\"b\"]}");
        Files.writeString(project.resolve(".claude/settings.local.json"), "{\"enabledMcpjsonServers\":[\"b\",\"c\"]}");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(project)).extracting(McpServerConfig::name, McpServerConfig::approval)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("a", McpApproval.APPROVED), org.assertj.core.groups.Tuple.tuple("b", McpApproval.DISABLED),
                        org.assertj.core.groups.Tuple.tuple("c", McpApproval.APPROVED));
    }

    @Test
    void enableAllInLocalSettingsOverridesSharedAndApprovesEverything() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".mcp.json"), "{\"mcpServers\":{\"a\":{\"command\":\"x\"}}}");
        Files.writeString(project.resolve(".claude/settings.json"), "{\"enableAllProjectMcpServers\":false}");
        Files.writeString(project.resolve(".claude/settings.local.json"), "{\"enableAllProjectMcpServers\":true}");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(project)).extracting(McpServerConfig::approval)
                .containsExactly(McpApproval.APPROVED);
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

    @Test
    void readsTheUserConfigFromClaudeConfigDirWhenSet() throws IOException {
        Files.writeString(project.resolve(".claude.json"), "{\"mcpServers\":{\"custom\":{\"command\":\"x\"}}}");
        var host = new FakeHostEnvironment(HostEnvironment.OperatingSystem.LINUX, home).withEnv("CLAUDE_CONFIG_DIR", project.toString());

        assertThat(new McpConfigReader(host).read(null)).extracting(McpServerConfig::name).containsExactly("custom");
    }

    @Test
    void userSettingsApproveAllButLocalSettingsOverrideIt() throws IOException {
        Files.createDirectories(home.resolve(".claude"));
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".mcp.json"), "{\"mcpServers\":{\"a\":{\"command\":\"x\"}}}");
        Files.writeString(home.resolve(".claude/settings.json"), "{\"enableAllProjectMcpServers\":true}");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(project)).extracting(McpServerConfig::approval).containsExactly(McpApproval.APPROVED);

        Files.writeString(project.resolve(".claude/settings.local.json"), "{\"enableAllProjectMcpServers\":false}");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(project)).extracting(McpServerConfig::approval).containsExactly(McpApproval.PENDING);
    }

    @Test
    void malformedSettingsFilesDoNotHideTheServers() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".mcp.json"), "{\"mcpServers\":{\"a\":{\"command\":\"x\"}}}");
        Files.writeString(project.resolve(".claude/settings.local.json"), "{ not json");

        assertThat(reader(HostEnvironment.OperatingSystem.LINUX).read(project)).extracting(McpServerConfig::name).containsExactly("a");
    }
}
