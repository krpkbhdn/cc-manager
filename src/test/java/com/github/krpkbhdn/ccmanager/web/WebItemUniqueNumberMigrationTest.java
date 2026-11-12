package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WebItemUniqueNumberMigrationTest {

    @TempDir
    Path temp;

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("upgrade.db"));
        Flyway.configure().dataSource(dataSource).target("24").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    void migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    void item(int id, int revision, int n) {
        jdbc.sql("INSERT INTO web_review_item (id, revision_id, page_id, n, anchor_json, screenshot_file)"
                + " VALUES (?, ?, 1, ?, '{}', ?)").params(id, revision, n, "item-" + id + ".png").update();
    }

    void seed() {
        jdbc.sql("INSERT INTO project (id, name, path) VALUES (1, 'a', '/a')").update();
        jdbc.sql("INSERT INTO web_review_revision (id, project_id, number, status) VALUES (1, 1, 1, 'SENT')").update();
        jdbc.sql("INSERT INTO web_review_revision (id, project_id, number, status) VALUES (2, 1, 2, 'SENT')").update();
        jdbc.sql("INSERT INTO web_review_page (id, project_id, page_key, last_url) VALUES (1, 1, 'k', 'http://x')").update();
        item(1, 1, 1);
        item(2, 1, 1);
        item(3, 1, 1);
        item(4, 1, 2);
        item(5, 2, 1);
    }

    String file(int id) {
        return jdbc.sql("SELECT screenshot_file FROM web_review_item WHERE id = ?").param(id).query(String.class)
                .optional().orElse(null);
    }

    int number(int id) {
        return jdbc.sql("SELECT n FROM web_review_item WHERE id = ?").param(id).query(Integer.class).single();
    }

    @Test
    void duplicatesAreRenumberedAboveTheRevisionMaximum() {
        seed();

        migrate();

        assertThat(number(1)).isEqualTo(1);
        assertThat(number(2)).isEqualTo(3);
        assertThat(number(3)).isEqualTo(4);
        assertThat(number(4)).isEqualTo(2);
        assertThat(number(5)).isEqualTo(1);
        List<Integer> clashes = jdbc.sql("SELECT COUNT(*) FROM web_review_item GROUP BY revision_id, n HAVING COUNT(*) > 1")
                .query(Integer.class).list();
        assertThat(clashes).isEmpty();
    }

    @Test
    void screenshotIsClearedOnlyForDuplicatedGroups() {
        seed();

        migrate();

        assertThat(file(1)).isNull();
        assertThat(file(2)).isNull();
        assertThat(file(3)).isNull();
        assertThat(file(4)).isEqualTo("item-4.png");
        assertThat(file(5)).isEqualTo("item-5.png");
    }

    @Test
    void duplicateNumberIsRejectedAfterwards() {
        seed();

        migrate();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM sqlite_master WHERE name = 'idx_web_review_item_revision_number'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> item(9, 1, 1)).hasMessageContaining("UNIQUE");
    }

    @Test
    void emptyTablesMigrateCleanly() {
        migrate();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_review_item").query(Integer.class).single()).isZero();
    }
}
