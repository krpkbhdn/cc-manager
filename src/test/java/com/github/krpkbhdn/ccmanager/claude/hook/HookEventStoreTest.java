package com.github.krpkbhdn.ccmanager.claude.hook;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

class HookEventStoreTest {

    private static final int RETENTION = 5;
    private static final int MAX_TEXT = 20;
    private static final int MAX_SCANNED = 1000;

    private JdbcClient jdbc;
    private HookEventStore store;

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("test.db"));
        jdbc = JdbcClient.create(dataSource);
        var schema = Files.readString(Path.of("src/main/resources/db/migration/V2__create_hook_event.sql"));
        for (var statement : schema.split(";")) {
            if (!statement.isBlank()) {
                jdbc.sql(statement).update();
            }
        }
        store = new HookEventStore(jdbc, JsonMapper.builder().build(), properties(MAX_SCANNED));
    }

    private static HookProperties properties(int maxScanned) {
        return new HookProperties(RETENTION, 1024, MAX_TEXT, java.time.Duration.ofSeconds(1), 10, maxScanned);
    }

    @Test
    void secretsAndUnreadToolFieldsAreNotStored() {
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"TOKEN=abc123\",\"env\":\"hidden\"},"
                + "\"tool_response\":\"out\"}"));
        store.save(of("PostToolUse", "{\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"a.txt\",\"content\":\"filebody\"}}"));

        var stored = String.join("", jdbc.sql("SELECT payload FROM hook_event").query(String.class).list());

        assertThat(stored).doesNotContain("abc123", "hidden", "out\"", "filebody").contains("TOKEN=[redacted]", "a.txt");
        assertThat(store.recent("s1", 10)).extracting(ActivityEntry::text).containsExactly("Bash: TOKEN=[redacted]", "Wrote a.txt");
    }

    @Test
    void aHugeUnbrokenTextIsCutBeforeRedactionSoTheHookThreadDoesNotStall() {
        var huge = "tokentoken".repeat(20_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> store.save(of("UserPromptSubmit", "{\"prompt\":\"" + huge + "\"}")));

        assertThat(jdbc.sql("SELECT payload FROM hook_event").query(String.class).single().length()).isLessThan(huge.length());
    }

    @Test
    void aSecretCrossingTheCutLimitLeaksNothingBeyondTheKeptPrefix() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"aaaa password=hunter2secret\"}"));
        store.save(of("UserPromptSubmit", "{\"prompt\":\"aaaaaaaaaa password=hunter2secret\"}"));

        var stored = String.join("", jdbc.sql("SELECT payload FROM hook_event").query(String.class).list());

        assertThat(stored).doesNotContain("hunter", "secret\"").contains("password=[redacted]");
    }

    @Test
    void recentStopsScanningAfterMaxScannedRows() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"old\"}"));
        for (int i = 0; i < 20; i++) {
            store.save(of("InstructionsLoaded"));
        }

        assertThat(new HookEventStore(jdbc, JsonMapper.builder().build(), properties(8)).recent("s1", 2)).isEmpty();
    }

    @Test
    void recentReturnsReadableEntriesNewestLast() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"hello\",\"session_id\":\"c1\"}"));
        store.save(of("InstructionsLoaded"));
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"},\"agent_id\":\"a1\"}"));
        store.save(of("Stop"));

        var entries = store.recent("s1", 10);

        assertThat(entries).extracting(ActivityEntry::text).containsExactly("Prompt: hello", "Bash: ls", "Finished");
        assertThat(entries.get(1).agentId()).isEqualTo("a1");
        assertThat(entries.get(0).agentId()).isNull();
        assertThat(store.recent("other", 10)).isEmpty();
    }

    @Test
    void recentHonoursLimitKeepingTheNewest() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"first\"}"));
        store.save(of("PreCompact"));
        store.save(of("Stop"));

        assertThat(store.recent("s1", 2)).extracting(ActivityEntry::text).containsExactly("Compacting context", "Finished");
    }

    @Test
    void recentPagesPastNewestRowsWithoutDescription() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"old\"}"));
        for (int i = 0; i < 20; i++) {
            store.save(of("InstructionsLoaded"));
        }

        assertThat(store.recent("s1", 2)).extracting(ActivityEntry::text).containsExactly("Prompt: old");
    }

    @Test
    void recentDoesNotRepeatEventsWhenRowsArriveBetweenPages() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"b\"}"));
        store.save(of("UserPromptSubmit", "{\"prompt\":\"a\"}"));
        for (int i = 0; i < 11; i++) {
            store.save(of("InstructionsLoaded"));
        }
        var spied = spy(jdbc);
        var queries = new AtomicInteger();
        doAnswer(call -> {
            if (queries.incrementAndGet() == 2) {
                for (int i = 0; i < 5; i++) {
                    store.save(of("Stop"));
                }
            }
            return call.callRealMethod();
        }).when(spied).sql(anyString());
        var paged = new HookEventStore(spied, JsonMapper.builder().build(), properties(MAX_SCANNED));

        assertThat(paged.recent("s1", 3)).extracting(ActivityEntry::text).containsExactly("Prompt: b", "Prompt: a");
    }

    @Test
    void longStringsAreTruncatedBeforeStoring() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"" + "p".repeat(100) + "\"}"));

        var stored = jdbc.sql("SELECT payload FROM hook_event").query(String.class).single();

        assertThat(stored).contains("p".repeat(MAX_TEXT)).doesNotContain("p".repeat(MAX_TEXT + 1));
    }

    @Test
    void pruneKeepsOnlyTheNewestRetentionRows() {
        for (var i = 0; i < RETENTION + 3; i++) {
            store.save(of("Stop"));
        }

        store.prune();

        assertThat(jdbc.sql("SELECT count(*) FROM hook_event").query(Integer.class).single()).isEqualTo(RETENTION);
    }
}
