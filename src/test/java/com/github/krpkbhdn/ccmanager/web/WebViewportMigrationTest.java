package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WebViewportMigrationTest {

    @TempDir
    Path temp;

    @Test
    void v23RowSurvivesV24WithNullViewportColumns() {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("m.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(ds).target("23").load().migrate();
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("INSERT INTO project (id, name, path) VALUES (1, 'a', '/a')").update();
        jdbc.sql("INSERT INTO web_review_revision (id, project_id, number) VALUES (1, 1, 1)").update();
        jdbc.sql("INSERT INTO web_review_page (id, project_id, page_key, last_url) VALUES (1, 1, 'k', 'http://x/')").update();
        jdbc.sql("INSERT INTO web_review_item (id, revision_id, page_id, n, anchor_json, comment) VALUES (7, 1, 1, 1, '{}', 'old')").update();

        Flyway.configure().dataSource(ds).load().migrate();

        var row = jdbc.sql("SELECT comment, viewport_width, viewport_height, device_name, device_category FROM web_review_item WHERE id = 7")
                .query((rs, i) -> new Object[] {rs.getString(1), rs.getObject(2), rs.getObject(3), rs.getObject(4), rs.getObject(5)}).single();
        assertThat(row).containsExactly("old", null, null, null, null);
        jdbc.sql("UPDATE web_review_item SET viewport_width = 390, viewport_height = 844, device_name = 'iPhone 14', device_category = 'MOBILE' WHERE id = 7")
                .update();
        assertThatThrownBy(() -> jdbc.sql("UPDATE web_review_item SET device_category = 'WATCH' WHERE id = 7").update()).isInstanceOf(Exception.class);
    }
}
