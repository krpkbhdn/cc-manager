package com.github.krpkbhdn.ccmanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DatabaseConfigurationTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 100;

    @TempDir
    Path temp;

    private HikariDataSource dataSource;
    private JdbcClient jdbc;
    private TransactionTemplate transaction;

    @BeforeEach
    void open() {
        dataSource = DatabaseConfiguration.create(temp.resolve("test.db"), new DatabaseProperties(Duration.ofSeconds(30), THREADS));
        jdbc = JdbcClient.create(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.sql("CREATE TABLE counter (id INTEGER PRIMARY KEY, value INTEGER NOT NULL)").update();
        jdbc.sql("CREATE TABLE plain (n INTEGER NOT NULL)").update();
        jdbc.sql("INSERT INTO counter (id, value) VALUES (1, 0)").update();
    }

    @AfterEach
    void close() {
        dataSource.close();
    }

    @Test
    void appliesPragmasAndPoolSize() {
        assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("wal");
        assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("PRAGMA synchronous").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("PRAGMA busy_timeout").query(Integer.class).single()).isEqualTo(30_000);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(THREADS);
    }

    @Test
    void readThenWriteTransactionsDoNotFailUnderContention() throws Exception {
        var pool = Executors.newFixedThreadPool(THREADS * 2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < ROUNDS; i++) {
                        transaction.executeWithoutResult(status -> {
                            long value = jdbc.sql("SELECT value FROM counter WHERE id = 1").query(Long.class).single();
                            jdbc.sql("UPDATE counter SET value = ? WHERE id = 1").param(value + 1).update();
                        });
                    }
                }));
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < ROUNDS; i++) {
                        jdbc.sql("INSERT INTO plain (n) VALUES (?)").param(i).update();
                    }
                }));
            }
            for (var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.sql("SELECT value FROM counter WHERE id = 1").query(Long.class).single()).isEqualTo((long) THREADS * ROUNDS);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM plain").query(Long.class).single()).isEqualTo((long) THREADS * ROUNDS);
    }
}
