package com.github.krpkbhdn.ccmanager.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SessionHistorySchemaTest {

    @TempDir
    Path temp;

    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @Test
    void sqliteIsBuiltWithFts5() {
        var used = jdbc.sql("SELECT sqlite_compileoption_used('ENABLE_FTS5')").query(Integer.class).single();

        assertThat(used).isEqualTo(1);
    }

    @Test
    void migrationCreatesHistoryTables() {
        var names = jdbc.sql("SELECT name FROM sqlite_master WHERE name IN ('transcript_file', 'transcript_entry_fts')")
                .query(String.class).list();

        assertThat(names).containsExactlyInAnyOrder("transcript_file", "transcript_entry_fts");
    }

    @Test
    void matchFindsCyrillicWordAndSnippetMarksIt() {
        jdbc.sql("INSERT INTO transcript_entry_fts (text, role, file_id, entry_uuid, line_offset, at) VALUES (?, 'user', 1, 'u1', 0, 0)")
                .param("Давай виправимо Помилку авторизації у модулі").update();

        var snippet = jdbc.sql("SELECT snippet(transcript_entry_fts, 0, char(2), char(3), '…', 8) FROM transcript_entry_fts WHERE transcript_entry_fts MATCH ?")
                .param("помилку")
                .query(String.class).single();

        assertThat(snippet).contains("\u0002Помилку\u0003");
    }
}
