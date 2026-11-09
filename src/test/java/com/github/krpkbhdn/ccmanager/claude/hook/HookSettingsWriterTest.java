package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class HookSettingsWriterTest {

    private static final String URL = "http://127.0.0.1:5555/hook/abc-1";
    private static final String STATUS_LINE_URL = "http://127.0.0.1:5555/statusline/abc-1";
    private static final String MCP_URL = "http://127.0.0.1:5555/mcp/abc-1";
    private static final String PROJECT_MCP_ENTRY = "{\"type\":\"http\",\"url\":\"" + MCP_URL + "\",\"headers\":{\"Authorization\":\"Bearer ${CCM_HOOK_TOKEN}\"}}";
    private static final String APP_MCP_URL = "http://127.0.0.1:5555/assistant-mcp";
    private static final String APP_TOKEN = "apptokenvalue";
    private static final String DEAD_PID = String.valueOf(Integer.MAX_VALUE);

    private static final CurlSupport NEW_CURL = new CurlSupport(executable -> "curl 8.16.0 (x86_64-w64-mingw32)");
    private static final CurlSupport OLD_CURL = new CurlSupport(executable -> "curl 7.81.0 (x86_64-pc-linux-gnu)");
    private static final HookProperties HOOK_PROPERTIES = new HookProperties(10, 1024, 500, Duration.ofSeconds(590), 10, 1000, 1000);

    private final JsonMapper mapper =JsonMapper.builder().build();
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
        writer = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, HOOK_PROPERTIES, NEW_CURL, false);
        windowsWriter = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, HOOK_PROPERTIES, NEW_CURL, true);
    }

    @Test
    void decisionHookTimeoutFollowsTheConfiguredHoldWithAMargin() throws Exception {
        var longHold = new HookProperties(10, 1024, 500, Duration.ofSeconds(900), 10, 1000, 1000);
        var file = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, longHold, NEW_CURL, false).write("abc-1").settings();

        var hooks = mapper.readTree(Files.readString(file)).path("hooks");

        assertThat(hooks.path("PermissionRequest").get(0).path("hooks").get(0).path("timeout").asInt()).isEqualTo(910);
    }

    @Test
    void rewritingASessionReplacesTheFileAndLeavesNoTemporaryFile() throws Exception {
        writer.write("abc-1");
        writer.write("abc-1");

        try (var files = Files.list(ownDir())) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .containsExactlyInAnyOrder("abc-1.json", "abc-1.mcp.json");
        }
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
        assertThat(handler.path("headers").toString()).isEqualTo("{\"Authorization\":\"Bearer $CCM_HOOK_TOKEN\"}");
        assertThat(handler.path("allowedEnvVars").toString()).isEqualTo("[\"CCM_HOOK_TOKEN\"]");
        assertThat(handler.toString()).doesNotContain("tok-abc");
    }

    @Test
    void permissionRequestAndElicitationWaitUpToTenMinutes() throws Exception {
        var hooks = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("hooks");

        assertThat(hooks.path("PermissionRequest").get(0).path("hooks").get(0).path("timeout").asInt()).isEqualTo(600);
        assertThat(hooks.path("Elicitation").get(0).path("hooks").get(0).path("timeout").asInt()).isEqualTo(600);
    }

    @Test
    void onPosixSessionStartIsAnAsyncShellCurlSendingTheBearerFromTheEnvironment() throws Exception {
        var handler = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("hooks").path("SessionStart").get(0)
                .path("hooks").get(0);

        assertThat(handler.path("type").asString()).isEqualTo("command");
        assertThat(handler.path("command").asString()).isEqualTo("curl -s -m 3 -X POST -H 'Content-Type: application/json' "
                + "--variable %CCM_HOOK_TOKEN --expand-header 'Authorization: Bearer {{CCM_HOOK_TOKEN}}' --data-binary '@-' '" + URL + "'");
        assertThat(handler.path("async").asBoolean()).isTrue();
    }

    @Test
    void anOldOrUnknownCurlFallsBackToTheShellExpandingTheVariable() throws Exception {
        var oldWriter = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, HOOK_PROPERTIES, OLD_CURL, false);
        var unknownWriter = new HookSettingsWriter(server, new AppProperties(dataDir), mapper, HOOK_PROPERTIES, new CurlSupport(executable -> ""), false);
        var expected = "curl -s -m 3 -X POST -H 'Content-Type: application/json' -H \"Authorization: Bearer $CCM_HOOK_TOKEN\" "
                + "--data-binary '@-' '" + STATUS_LINE_URL + "'";

        assertThat(mapper.readTree(Files.readString(oldWriter.write("abc-1").settings())).path("statusLine").path("command").asString()).isEqualTo(expected);
        assertThat(mapper.readTree(Files.readString(unknownWriter.write("abc-1").settings())).path("statusLine").path("command").asString()).isEqualTo(expected);
    }

    @Test
    void onPosixStatusLineCommandPostsItsStdinToTheStatusLineEndpoint() throws Exception {
        var statusLine = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("statusLine");

        assertThat(statusLine.path("type").asString()).isEqualTo("command");
        assertThat(statusLine.path("command").asString()).isEqualTo("curl -s -m 3 -X POST -H 'Content-Type: application/json' "
                + "--variable %CCM_HOOK_TOKEN --expand-header 'Authorization: Bearer {{CCM_HOOK_TOKEN}}' --data-binary '@-' '" + STATUS_LINE_URL + "'");
    }

    @Test
    void onWindowsCurlExeAlsoTakesTheBearerFromTheEnvironmentAndNoSideFileIsWritten() throws Exception {
        var json = mapper.readTree(Files.readString(windowsWriter.write("abc-1").settings()));

        var handler = json.path("hooks").path("SessionStart").get(0).path("hooks").get(0);
        assertThat(handler.path("command").asString()).isEqualTo("curl.exe -s -m 3 -X POST -H \"Content-Type: application/json\" "
                + "--variable %CCM_HOOK_TOKEN --expand-header \"Authorization: Bearer {{CCM_HOOK_TOKEN}}\" --data-binary \"@-\" \"" + URL + "\"");
        assertThat(json.path("statusLine").path("command").asString()).startsWith("curl.exe ").contains("\"" + STATUS_LINE_URL + "\"")
                .doesNotContain("$");
        try (var files = Files.list(ownDir())) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactlyInAnyOrder("abc-1.json", "abc-1.mcp.json");
        }
    }

    @Test
    void assistantMcpFileReferencesTheTokenVariableAndHoldsNoSecret() throws Exception {
        when(server.assistantMcpUrl()).thenReturn(APP_MCP_URL);
        when(server.assistantMcpToken()).thenReturn(APP_TOKEN);

        var files = writer.writeAssistant("abc-1");

        var text = Files.readString(files.mcpConfig());
        var entry = mapper.readTree(text).path("mcpServers").path("cc-manager-app");
        assertThat(entry.path("url").asString()).isEqualTo(APP_MCP_URL);
        assertThat(entry.path("headers").path("Authorization").asString()).isEqualTo("Bearer ${CCM_ASSISTANT_TOKEN}");
        assertThat(text).doesNotContain(APP_TOKEN);
        assertThat(Files.readString(files.settings())).doesNotContain(APP_TOKEN);
        assertThat(Files.readString(writer.write("abc-1").mcpConfig())).doesNotContain("CCM_ASSISTANT_TOKEN");
    }

    @Test
    void projectSessionsAndTheAssistantBothDenyReadingTheHooksDirectory() throws Exception {
        when(server.assistantMcpUrl()).thenReturn(APP_MCP_URL);
        var deny = mapper.readTree(Files.readString(writer.write("abc-1").settings())).path("permissions").path("deny");
        var assistant = mapper.readTree(Files.readString(writer.writeAssistant("abc-1").settings())).path("permissions");

        assertThat(deny.get(0).asString()).startsWith("Read(/").endsWith("/hooks/**)");
        assertThat(assistant.path("deny").get(0).asString()).isEqualTo(deny.get(0).asString());
        var windowsDeny = mapper.readTree(Files.readString(windowsWriter.write("abc-1").settings())).path("permissions").path("deny");
        assertThat(windowsDeny.get(0).asString()).doesNotContain(":", "\\").endsWith("/hooks/**)");
    }

    @Test
    void writesAnMcpConfigPointingAtTheMcpEndpointAndPreApprovesItsTools() throws Exception {
        var files = writer.write("abc-1");

        assertThat(files.mcpConfig()).isEqualTo(ownDir().resolve("abc-1.mcp.json"));
        assertThat(mapper.readTree(Files.readString(files.mcpConfig())).path("mcpServers").path("cc-manager").toString())
                .isEqualTo(PROJECT_MCP_ENTRY);
        assertThat(Files.readString(files.mcpConfig())).doesNotContain("secret", "tok-abc");
        assertThat(mapper.readTree(Files.readString(files.settings())).path("permissions").path("allow").toString()).isEqualTo("[\"mcp__cc-manager__list_run_configurations\"]");
    }

    @Test
    void extraServersAreMergedButTheAppsOwnServerNameStaysReserved() throws Exception {
        var extra = new java.util.LinkedHashMap<String, tools.jackson.databind.JsonNode>();
        extra.put("db", mapper.readTree("{\"command\":\"db-mcp\"}"));
        extra.put("cc-manager", mapper.readTree("{\"command\":\"evil\"}"));

        var servers = mapper.readTree(Files.readString(writer.write("abc-1", extra).mcpConfig())).path("mcpServers");

        assertThat(servers.path("db").toString()).isEqualTo("{\"command\":\"db-mcp\"}");
        assertThat(servers.path("cc-manager").toString()).isEqualTo(PROJECT_MCP_ENTRY);
    }

    @Test
    void onlyTheLauncherWritesSessionFilesSoEveryLaunchGetsTheTokenVariable() throws Exception {
        try (var sources = Files.walk(Path.of("src/main"))) {
            var users = sources.filter(file -> file.toString().endsWith(".java") || file.toString().endsWith(".kt"))
                    .filter(file -> !file.getFileName().toString().startsWith("HookSettingsWriter"))
                    .filter(file -> read(file).contains("HookSettingsWriter")).map(file -> file.getFileName().toString()).toList();

            assertThat(users).containsExactly("ClaudeLauncher.java");
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void onPosixTheMcpConfigIsOwnerOnly()throws Exception {
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
    void startupKeepsTheDirectoryOfALivePidHoweverOldItIs() throws Exception {
        var otherPid = ProcessHandle.current().parent();
        assumeTrue(otherPid.isPresent());
        var directory = Files.createDirectories(dataDir.resolve("hooks").resolve(String.valueOf(otherPid.get().pid())));
        writer.deleteStaleFiles();
        assertThat(directory).exists();

        Files.setLastModifiedTime(directory, FileTime.from(Instant.now().minus(Duration.ofDays(30))));
        writer.deleteStaleFiles();

        assertThat(directory).exists();
    }

    @Test
    void aSecondWriterInTheSameProcessLeavesTheRunningOnesFilesAlone() throws Exception {
        var live = writer.write("abc-1").settings();

        new HookSettingsWriter(server, new AppProperties(dataDir), mapper, HOOK_PROPERTIES, NEW_CURL, false).deleteStaleFiles();

        assertThat(live).exists();
    }
}
