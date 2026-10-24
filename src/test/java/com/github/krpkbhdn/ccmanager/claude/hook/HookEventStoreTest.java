package com.github.krpkbhdn.ccmanager.claude.hook;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

class HookEventStoreTest {

    private static final int RETENTION = 5;
    private static final int MAX_TEXT = 20;

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
        store = new HookEventStore(jdbc, JsonMapper.builder().build(), new HookProperties(RETENTION, 1024, MAX_TEXT));
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
