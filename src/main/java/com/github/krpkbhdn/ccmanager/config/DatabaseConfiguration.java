package com.github.krpkbhdn.ccmanager.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQLite in WAL mode: readers do not block the writer. Transactions start with BEGIN IMMEDIATE so a read-then-write
 * transaction never fails on a lock upgrade (busy_timeout does not cover it); writers queue on busy_timeout instead.
 */
@Configuration
@EnableConfigurationProperties({AppProperties.class, DatabaseProperties.class})
public class DatabaseConfiguration {

    @Bean
    DataSource dataSource(AppProperties properties, DatabaseProperties database) throws IOException {
        Files.createDirectories(properties.dataDir());
        return create(properties.dataDir().resolve("cc-manager.db"), database);
    }

    public static HikariDataSource create(Path file, DatabaseProperties database) {
        var source = new HikariDataSource();
        source.setJdbcUrl("jdbc:sqlite:" + file + "?journal_mode=WAL&synchronous=NORMAL&foreign_keys=on&transaction_mode=IMMEDIATE"
                + "&busy_timeout=" + database.busyTimeout().toMillis());
        source.setMaximumPoolSize(database.maxConnections());
        source.setMinimumIdle(1);
        return source;
    }
}
