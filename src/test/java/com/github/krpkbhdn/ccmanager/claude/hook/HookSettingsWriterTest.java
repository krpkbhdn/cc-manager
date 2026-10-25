package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class HookSettingsWriterTest {

    private static final String URL = "http://127.0.0.1:5555/hook/secret/abc-1";
    private static final String STATUS_LINE_URL = "http://127.0.0.1:5555/statusline/secret/abc-1";
    private static final String MCP_URL = "http://127.0.0.1:5555/mcp/secret/abc-1";
    private static final String DEAD_PID = String.valueOf(Integer.MAX_VALUE);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private HookSettingsWriter writer;
    private HookSettingsWriter windowsWriter;
    private HookServer server;

    @TempDir
    Path dataDir;

    @BeforeEach
    void setUp() {
        server = mock(HookServer.class);
        when(server.hookUrl("abc-1")).thenReturn(URL);
        when(server.statusLineUrl("abc-1")).thenReturn(STATUS_LINE_URL);
        when(server.mcpUrl("abc-1")).thenReturn(MCP_URL);
        writer = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, false);
        windowsWriter = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, true);
    }

    private Path ownDir() {
        return dataDir.resolve("hooks").resolve(String.valueOf(ProcessHandle.current().pid()));
    }

    @Test
    void writesHooksStatusLineAndMcpPermissionOnly() throws Exception {
        var file = writer.write("abc-1").settings();

        var json = mapper.readTree(Files.readString(file));
        assertThat(file).isEqualTo(ownDir().resolve("abc-1.json"));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("hooks", "statusLine", "permissions");
    }

    @Test
    void onPosixOnlyTheOwnerCanReadTheDirectoryAndFile() throws Exception {
        assumeTrue(dataDir.getFileSystem().supportedFileAttributeViews().contains("posix"));

        var file = writer.write("abc-1").settings();

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.getParent()))).isEqualTo("rwx------");
    }

    @Test
    void subscribesToEveryEventExceptTheDangerousOnes() throws Exception {
        var hooks = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("hooks");

        assertThat(hooks.propertyNames()).contains("PreToolUse", "PermissionRequest", "Stop", "SessionEnd", "SessionStart")
                .doesNotContain("WorktreeCreate", "WorktreeRemove", "Setup", "MessageDisplay", "");
        assertThat(hooks.size()).isEqualTo(HookEventType.values().length - 5);
    }

    @Test
    void usesShortTimeoutHttpHandlersWithoutMatcher() throws Exception {
        var entry = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("hooks").path("PreToolUse").get(0);

        assertThat(entry.has("matcher")).isFalse();
        var handler = entry.path("hooks").get(0);
        assertThat(handler.path("type").asString()).isEqualTo("http");
        assertThat(handler.path("url").asString()).isEqualTo(URL);
        assertThat(handler.path("timeout").asInt()).isEqualTo(5);
    }

    @Test
    void onPosixSessionStartUsesAsyncExecFormCurlReadingItsUrlFromAConfigFile() throws Exception {
        var handler = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("hooks").path("SessionStart").get(0)
                .path("hooks").get(0);

        assertThat(handler.path("type").asString()).isEqualTo("command");
        assertThat(handler.path("command").asString()).isEqualTo("curl");
        assertThat(handler.path("async").asBoolean()).isTrue();
        assertThat(handler.path("args").toString()).contains("-K", "--data-binary", "@-", "abc-1.json.hook.curl").doesNotContain(URL);
        assertThat(Files.readString(ownDir().resolve("abc-1.json.hook.curl"))).isEqualTo("url = \"" + URL + "\"\n");
    }

    @Test
    void onPosixStatusLineCommandPostsItsStdinToTheStatusLineEndpoint() throws Exception {
        var statusLine = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("statusLine");

        assertThat(statusLine.path("type").asString()).isEqualTo("command");
        assertThat(statusLine.path("command").asString()).startsWith("curl ").contains("\"-K\"", "\"--data-binary\" \"@-\"", "abc-1.json.status.curl")
                .doesNotContain(STATUS_LINE_URL);
        assertThat(Files.readString(ownDir().resolve("abc-1.json.status.curl"))).isEqualTo("url = \"" + STATUS_LINE_URL + "\"\n");
    }

    @Test
    void onWindowsTheUrlStaysAnArgumentAndNoCurlFileIsWritten() throws Exception {
        var json = mapper.readTree(Files.readString(windowsWriter.write("abc-1").settings()));

        var handler = json.path("hooks").path("SessionStart").get(0).path("hooks").get(0);
        assertThat(handler.path("command").asString()).isEqualTo("curl.exe");
        assertThat(handler.path("args").toString()).contains(URL).doesNotContain("-K", ".curl");
        assertThat(json.path("statusLine").path("command").asString()).startsWith("curl.exe ").contains("\"" + STATUS_LINE_URL + "\"")
                .doesNotContain("-K", ".curl");
        try (var files = Files.list(ownDir())) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactlyInAnyOrder("abc-1.json", "abc-1.mcp.json");
        }
    }

    @Test
    void writesAnMcpConfigPointingAtTheMcpEndpointAndPreApprovesItsTools() throws Exception {
        var files = writer.write("abc-1");

        assertThat(files.mcpConfig()).isEqualTo(ownDir().resolve("abc-1.mcp.json"));
        assertThat(mapper.readTree(Files.readString(files.mcpConfig())).path("mcpServers").path("cc-manager").toString())
                .isEqualTo("{\"type\":\"http\",\"url\":\"" + MCP_URL + "\"}");
        assertThat(mapper.readTree(Files.readString(files.settings())).path("permissions").path("allow").toString()).isEqualTo("[\"mcp__cc-manager\"]");
    }

    @Test
    void onPosixTheMcpConfigIsOwnerOnly() throws Exception {
        assumeTrue(dataDir.getFileSystem().supportedFileAttributeViews().contains("posix"));

        var file = writer.write("abc-1").mcpConfig();

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
    }

    @Test
    void deleteRemovesFileAndRejectsPathTraversal() {
        var file = writer.write("abc-1").settings();

        writer.delete("abc-1");

        assertThat(file).doesNotExist();
        assertThat(ownDir()).isEmptyDirectory();
        assertThatThrownBy(() -> writer.write("../evil")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startupRemovesTheFilesOfDeadProcessesAndLooseOldOnesButKeepsLiveOnes() throws Exception {
        var live = writer.write("abc-1").settings();
        var deadDir = Files.createDirectories(dataDir.resolve("hooks").resolve(DEAD_PID));
        var deadFile = Files.writeString(deadDir.resolve("old.json"), "{}");
        var loose = Files.writeString(dataDir.resolve("hooks").resolve("legacy.json"), "{}");

        writer.deleteStaleFiles();

        assertThat(deadDir).doesNotExist();
        assertThat(deadFile).doesNotExist();
        assertThat(loose).doesNotExist();
        assertThat(live).exists();
    }

    @Test
    void aSecondWriterInTheSameProcessLeavesTheRunningOnesFilesAlone() throws Exception {
        var live = writer.write("abc-1").settings();

        new HookSettingsWriter(server, new AppProperties(dataDir), mapper, false).deleteStaleFiles();

        assertThat(live).exists();
    }
}
