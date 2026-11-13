package com.github.krpkbhdn.ccmanager.claude.hook;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;

import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

class HookEventStoreTest {

    private static final int RETENTION = 5;
    /** Large enough that the per-session floor (retention / 100) is 2, not 0. */
    private static final int ROOMY_RETENTION = 200;
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
        store = new HookEventStore(jdbc, JsonMapper.builder().build(), properties(MAX_SCANNED), Runnable::run);
    }

    private static HookProperties properties(int maxScanned) {
        return new HookProperties(RETENTION, 1024, MAX_TEXT, java.time.Duration.ofSeconds(1), 10, maxScanned, 1000);
    }

    @Test
    void secretsAndUnreadToolFieldsAreNotStored() {
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"TOKEN=abc123\",\"env\":\"hidden\"},"
                + "\"tool_response\":\"out\"}"));
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"echo '{\\\"password\\\":\\\"hunter2\\\"}'\"}}"));
        store.save(of("PostToolUse", "{\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"a.txt\",\"content\":\"filebody\"}}"));

        var stored = String.join("", jdbc.sql("SELECT payload FROM hook_event").query(String.class).list());

        assertThat(stored).doesNotContain("abc123", "hidden", "out\"", "filebody", "hunter2").contains("[redacted]", "a.txt");
        assertThat(store.recent("s1", 10)).extracting(ActivityEntry::text).contains("Bash: TOKEN=[redacted]", "Wrote a.txt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"curl -H 'Authorization: Basic dXNlcjpwdw==' x", "curl -u admin:hunter2 http://h", "psql postgres://u:hunter2@host/db",
            "run --password hunter2", "echo ghp_abcdefghijklmnopqrstuvwx", "echo sk-abcdefghijklmnopqrstuv"})
    void commandSecretsOfAnyShapeAreMaskedBeforeStoring(String command) {
        store = new HookEventStore(jdbc, JsonMapper.builder().build(),
                new HookProperties(RETENTION, 1024, 200, Duration.ofSeconds(1), 10, MAX_SCANNED, 1000), Runnable::run);

        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"" + command + "\"}}"));

        var stored = jdbc.sql("SELECT payload FROM hook_event").query(String.class).single();
        assertThat(stored).contains("[redacted]").doesNotContain("hunter2", "dXNlcjpwdw", "abcdefghijklmnop");
    }

    @Test
    void aHugeUnbrokenTextIsCutBeforeRedactionSoTheHookThreadDoesNotStall() {
        var huge = "tokentoken".repeat(20_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> store.save(of("UserPromptSubmit", "{\"prompt\":\"" + huge + "\"}")));

        assertThat(jdbc.sql("SELECT payload FROM hook_event").query(String.class).single().length()).isLessThan(huge.length());
    }

    @Test
    void aTokenHalfCutByTheLookaheadWindowIsDroppedWhenMaskingShrinksTheText() {
        store = new HookEventStore(jdbc, JsonMapper.builder().build(),
                new HookProperties(RETENTION, 100_000, 2000, Duration.ofSeconds(1), 10, MAX_SCANNED, 1000), Runnable::run);
        var text = "x sk-abcdefghijklmnopqrstuv ".repeat(100);

        store.save(of("UserPromptSubmit", "{\"prompt\":\"" + text + "\"}"));

        var stored = jdbc.sql("SELECT payload FROM hook_event").query(String.class).single();
        assertThat(stored).doesNotContain("sk-abc").contains("[redacted]");
    }

    @Test
    void nonCommandFieldsAreMaskedStrictly() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"DB_PASS=hunter2 and key 0123456789abcdef0123456789abcdef01234567\"}"));

        var stored = jdbc.sql("SELECT payload FROM hook_event").query(String.class).single();

        assertThat(stored).doesNotContain("hunter2", "0123456789abcdef");
    }

    @Test
    void aSecretCrossingTheCutLimitLeaksNothingBeyondTheKeptPrefix() {
        store.save(of("UserPromptSubmit", "{\"prompt\":\"aaaa password=hunter2secret\"}"));
        store.save(of("UserPromptSubmit", "{\"prompt\":\"aaaaaaaaaa password=hunter2secret\"}"));

        var stored = String.join("", jdbc.sql("SELECT payload FROM hook_event").query(String.class).list());

        assertThat(stored).doesNotContain("hunter", "secret\"");
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
        store.save(of("UserPromptSubmit", "{\"prompt\":\"" + "p ".repeat(50) + "\"}"));

        var stored = jdbc.sql("SELECT payload FROM hook_event").query(String.class).single();

        assertThat(stored).contains("p ".repeat(MAX_TEXT / 2)).doesNotContain("p ".repeat(MAX_TEXT / 2 + 1));
    }

    @Test
    void pruneKeepsAQuietSessionsRowsWhileABusySessionOverflowsRetention() {
        var roomy = new HookEventStore(jdbc, JsonMapper.builder().build(),
                new HookProperties(ROOMY_RETENTION, 1024, MAX_TEXT, Duration.ofSeconds(1), 10, MAX_SCANNED, 1000), Runnable::run);
        roomy.save(justNow("quiet", of("UserPromptSubmit", "{\"prompt\":\"quiet\"}")));
        for (var i = 0; i < ROOMY_RETENTION + 100; i++) {
            roomy.save(justNow("s1", of("Stop")));
        }

        roomy.prune();

        assertThat(roomy.recent("quiet", 10)).extracting(ActivityEntry::text).containsExactly("Prompt: quiet");
    }

    private static HookEvent justNow(String ccmSessionId, HookEvent event) {
        return new HookEvent(ccmSessionId, event.type(), event.rawName(), event.payload(), java.time.Instant.now());
    }

    @Test
    void pruneDropsAQuietSessionsRowsOnceTheyAreOlderThanAWeek() {
        var roomy = new HookEventStore(jdbc, JsonMapper.builder().build(),
                new HookProperties(ROOMY_RETENTION, 1024, MAX_TEXT, Duration.ofSeconds(1), 10, MAX_SCANNED, 1000), Runnable::run);
        var prompt = of("UserPromptSubmit", "{\"prompt\":\"quiet\"}");
        roomy.save(new HookEvent("quiet", prompt.type(), prompt.rawName(), prompt.payload(), prompt.receivedAt()));
        jdbc.sql("UPDATE hook_event SET received_at = ?").param(java.time.Instant.now().minus(Duration.ofDays(30)).toString()).update();
        for (var i = 0; i < ROOMY_RETENTION + 100; i++) {
            roomy.save(of("Stop"));
        }

        roomy.prune();

        assertThat(roomy.recent("quiet", 10)).isEmpty();
    }

    @Test
    void pruneKeepsOnlyTheNewestRetentionRows() {
        for (var i = 0; i < RETENTION + 3; i++) {
            store.save(of("Stop"));
        }

        store.prune();

        assertThat(jdbc.sql("SELECT count(*) FROM hook_event").query(Integer.class).single()).isEqualTo(RETENTION);
    }

    @Test
    void saveDoesNotWaitForThePruneAndRetentionHoldsOnceItRuns() {
        var queued = new java.util.ArrayList<Runnable>();
        var async = new HookEventStore(jdbc, JsonMapper.builder().build(), properties(MAX_SCANNED), queued::add);

        for (var i = 0; i < 250; i++) {
            async.save(of("Stop"));
        }

        assertThat(queued).hasSize(1);
        assertThat(jdbc.sql("SELECT count(*) FROM hook_event").query(Integer.class).single()).isEqualTo(250);
        queued.getFirst().run();
        assertThat(jdbc.sql("SELECT count(*) FROM hook_event").query(Integer.class).single()).isEqualTo(RETENTION);
        async.save(of("Stop"));
        for (var i = 0; i < 100; i++) {
            async.save(of("Stop"));
        }
        assertThat(queued).hasSize(2);
    }

    @Test
    void gitShasAndPathsSurviveWhileASecretCrossingTheCutIsMasked() {
        store = new HookEventStore(jdbc, JsonMapper.builder().build(),
                new HookProperties(RETENTION, 1024, 60, Duration.ofSeconds(1), 10, MAX_SCANNED, 1000), Runnable::run);
        var sha = "git show 0123456789abcdef0123456789abcdef01234567";
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"" + sha + "\"}}"));
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls src/task-runner-configuration/ --max-tokens 4096\"}}"));
        store.save(of("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"" + "x".repeat(50) + " sk-abcdefghijklmnopqrstuvwxyz\"}}"));

        var stored = jdbc.sql("SELECT payload FROM hook_event ORDER BY id").query(String.class).list();

        assertThat(stored.get(0)).contains("0123456789abcdef0123456789abcdef01234567");
        assertThat(stored.get(1)).contains("src/task-runner-configuration/", "--max-tokens 4096");
        assertThat(stored.get(2)).doesNotContain("sk-abcdefghij");
    }
}
