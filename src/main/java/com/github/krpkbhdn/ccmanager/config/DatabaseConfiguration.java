package com.github.krpkbhdn.ccmanager.config;

import java.io.IOException;
import java.nio.file.Files;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** SQLite in WAL mode: readers do not block the writer; busy_timeout covers writer contention. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
class DatabaseConfiguration {

    private static final String PRAGMAS = "?journal_mode=WAL&foreign_keys=on&busy_timeout=5000";

    @Bean
    DataSource dataSource(AppProperties properties) throws IOException {
        Files.createDirectories(properties.dataDir());
        return DataSourceBuilder.create()
                .url("jdbc:sqlite:" + properties.dataDir().resolve("cc-manager.db") + PRAGMAS)
                .build();
    }
}
