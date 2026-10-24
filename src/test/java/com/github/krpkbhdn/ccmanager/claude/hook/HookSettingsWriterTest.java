package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class HookSettingsWriterTest {

    private static final String URL = "http://127.0.0.1:5555/hook/secret/abc-1";
    private static final String STATUS_LINE_URL = "http://127.0.0.1:5555/statusline/secret/abc-1";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private HookSettingsWriter writer;

    @TempDir
    Path dataDir;

    @BeforeEach
    void setUp() {
        var server = mock(HookServer.class);
        when(server.hookUrl("abc-1")).thenReturn(URL);
        when(server.statusLineUrl("abc-1")).thenReturn(STATUS_LINE_URL);
        writer = new HookSettingsWriter(server, new AppProperties(dataDir), mapper);
    }

    @Test
    void writesHooksAndStatusLineOnly() throws Exception {
        var file = writer.write("abc-1");

        var json = mapper.readTree(Files.readString(file));
        assertThat(file).isEqualTo(dataDir.resolve("hooks").resolve("abc-1.json"));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("hooks", "statusLine");
    }

    @Test
    void subscribesToEveryEventExceptTheDangerousOnes() throws Exception {
        var hooks = mapper.readTree(Files.readString(writer.write("abc-1"))).path("hooks");

        assertThat(hooks.propertyNames()).contains("PreToolUse", "PermissionRequest", "Stop", "SessionEnd", "SessionStart")
                .doesNotContain("WorktreeCreate", "WorktreeRemove", "Setup", "MessageDisplay", "");
        assertThat(hooks.size()).isEqualTo(HookEventType.values().length - 5);
    }

    @Test
    void usesShortTimeoutHttpHandlersWithoutMatcher() throws Exception {
        var entry = mapper.readTree(Files.readString(writer.write("abc-1"))).path("hooks").path("PreToolUse").get(0);

        assertThat(entry.has("matcher")).isFalse();
        var handler = entry.path("hooks").get(0);
        assertThat(handler.path("type").asString()).isEqualTo("http");
        assertThat(handler.path("url").asString()).isEqualTo(URL);
        assertThat(handler.path("timeout").asInt()).isEqualTo(5);
    }

    @Test
    void sessionStartUsesAsyncExecFormCurlBecauseHttpIsRejected() throws Exception {
        var handler = mapper.readTree(Files.readString(writer.write("abc-1"))).path("hooks").path("SessionStart").get(0)
                .path("hooks").get(0);

        assertThat(handler.path("type").asString()).isEqualTo("command");
        assertThat(handler.path("command").asString()).startsWith("curl");
        assertThat(handler.path("async").asBoolean()).isTrue();
        assertThat(handler.path("args").get(handler.path("args").size() - 1).asString()).isEqualTo(URL);
        assertThat(handler.path("args").toString()).contains("--data-binary", "@-");
    }

    @Test
    void statusLineCommandPostsItsStdinToTheStatusLineEndpoint() throws Exception {
        var statusLine = mapper.readTree(Files.readString(writer.write("abc-1"))).path("statusLine");

        assertThat(statusLine.path("type").asString()).isEqualTo("command");
        assertThat(statusLine.path("command").asString()).startsWith("curl").contains("\"--data-binary\" \"@-\"")
                .endsWith("\"" + STATUS_LINE_URL + "\"");
    }

    @Test
    void deleteRemovesFileAndRejectsPathTraversal() {
        var file = writer.write("abc-1");

        writer.delete("abc-1");

        assertThat(file).doesNotExist();
        assertThatThrownBy(() -> writer.write("../evil")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleFilesFromEarlierRunsAreRemovedAtStartup() throws Exception {
        var stale = writer.write("abc-1");

        writer.deleteStaleFiles();

        assertThat(stale).doesNotExist();
    }
}
