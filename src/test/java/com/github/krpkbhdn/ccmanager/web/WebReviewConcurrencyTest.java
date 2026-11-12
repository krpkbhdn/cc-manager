package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.config.DatabaseConfiguration;
import com.github.krpkbhdn.ccmanager.config.DatabaseProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Page visits, new items, status changes and sends from several threads on the app's real pooled data source must never fail with SQLITE_BUSY. */
class WebReviewConcurrencyTest {

    private static final int THREADS = 4;
    private static final long RUN_MILLIS = 3_000;

    @TempDir
    Path temp;

    private HikariDataSource dataSource;
    private WebReviewService service;
    private long projectId;

    @BeforeEach
    void setUp() {
        dataSource = DatabaseConfiguration.create(temp.resolve("app.db"), new DatabaseProperties(Duration.ofSeconds(5), THREADS));
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        var properties = new WebReviewProperties(600, List.of("display"), 1024, 1_000_000);
        service = new WebReviewService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                new WebScreenshotStore(new AppProperties(temp), properties), new WebFeedbackWriter(properties));
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
    }

    @AfterEach
    void close() {
        dataSource.close();
    }

    @Test
    void concurrentWritersNeverSeeABusyDatabase() throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        var pool = Executors.newFixedThreadPool(THREADS);
        var deadline = System.currentTimeMillis() + RUN_MILLIS;
        var anchor = new ElementAnchor("p", "/p", "p", null, null, "", "", "", Map.of());
        for (var t = 0; t < THREADS; t++) {
            var worker = t;
            pool.submit(() -> {
                var round = 0;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        var url = "http://localhost:3000/p" + (round % 5);
                        service.touchPage(projectId, url, "t" + worker);
                        var item = service.addItem(projectId, url, "t", anchor, "c", List.of());
                        service.setStatus(item.id(), WebItemStatus.DISMISSED);
                        service.setStatus(item.id(), WebItemStatus.OPEN);
                        service.updateItem(item.id(), "d", List.of());
                        if (worker == 0 && round % 10 == 9) {
                            var revision = service.currentRevision(projectId);
                            service.prepareFeedback(revision.id());
                            service.approve(revision.id());
                            service.startNextRevision(projectId);
                        }
                        round++;
                    } catch (IllegalStateException | IllegalArgumentException expectedRace) {
                        round++;
                    } catch (Throwable e) {
                        failures.add(e);
                        return;
                    }
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        assertThat(service.revisions(projectId)).isNotEmpty();
    }
}
