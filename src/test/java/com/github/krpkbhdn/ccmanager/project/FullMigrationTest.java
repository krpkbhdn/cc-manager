package com.github.krpkbhdn.ccmanager.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FullMigrationTest {

    @TempDir
    Path temp;

    @Test
    void allMigrationsOnEmptyDatabaseCreateKeyTablesIndexesAndColumns() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("empty.db") + "?foreign_keys=on");

        Flyway.configure().dataSource(dataSource).load().migrate();

        var jdbc = JdbcClient.create(dataSource);
        assertThat(jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table'").query(String.class).list())
                .contains("project", "hook_event", "team", "team_role", "run_configuration", "author_item_decision", "project_preset", "prompt_template");
        assertThat(jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'index'").query(String.class).list())
                .contains("idx_hook_event_session");
        assertThat(jdbc.sql("SELECT name FROM pragma_table_info('project')").query(String.class).list())
                .contains("model", "effort", "team_id", "is_group", "group_id");
        assertThat(jdbc.sql("SELECT name FROM pragma_table_info('run_configuration')").query(String.class).list())
                .contains("confirmed");
        assertThat(jdbc.sql("SELECT name FROM pragma_table_info('prompt_template')").query(String.class).list())
                .contains("description");
        assertThat(jdbc.sql("SELECT count(*) FROM team").query(Integer.class).single()).isPositive();
    }

    @Test
    void databaseWithRetiredVersion25InHistoryStillMigrates() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("retired.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).target("24").load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)"
                + " VALUES (25, '25', 'web review item shape', 'SQL', 'V25__web_review_item_shape.sql', 1, 'test', 1, 1)").update();

        Flyway.configure().dataSource(dataSource).ignoreMigrationPatterns("*:missing").load().migrate();

        assertThat(jdbc.sql("SELECT name FROM pragma_table_info('team')").query(String.class).list())
                .doesNotContain("lead_role_id");
    }
}
