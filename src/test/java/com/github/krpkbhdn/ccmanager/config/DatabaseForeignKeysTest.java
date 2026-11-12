package com.github.krpkbhdn.ccmanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.web.ElementAnchor;
import com.github.krpkbhdn.ccmanager.web.WebFeedbackWriter;
import com.github.krpkbhdn.ccmanager.web.WebReviewProperties;
import com.github.krpkbhdn.ccmanager.web.WebReviewService;
import com.github.krpkbhdn.ccmanager.web.WebScreenshotStore;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The app's own pooled data source (not a test-specific one) must enforce foreign keys, or deleting a project would leave web review rows behind. */
class DatabaseForeignKeysTest {

    @TempDir
    Path temp;

    private HikariDataSource dataSource;

    @AfterEach
    void close() {
        dataSource.close();
    }

    @Test
    void removingAProjectCascadesToItsWebReviewRowsOnEveryPooledConnection() {
        dataSource = DatabaseConfiguration.create(temp.resolve("app.db"), new DatabaseProperties(Duration.ofSeconds(5), 2));
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        var properties = new WebReviewProperties(600, List.of("display"), 1024, 50);
        var service = new WebReviewService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                new WebScreenshotStore(new AppProperties(temp), properties), new WebFeedbackWriter(properties));
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        var projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        service.addItem(projectId, "http://localhost:3000/a", "A", new ElementAnchor("p", "/p", "p", null, null, "", "", "", null), "c", List.of());

        assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
        jdbc.sql("DELETE FROM project WHERE id = ?").param(projectId).update();

        assertThat(jdbc.sql("SELECT (SELECT COUNT(*) FROM web_review_item) + (SELECT COUNT(*) FROM web_review_page) "
                + "+ (SELECT COUNT(*) FROM web_review_revision)").query(Integer.class).single()).isZero();
    }
}
