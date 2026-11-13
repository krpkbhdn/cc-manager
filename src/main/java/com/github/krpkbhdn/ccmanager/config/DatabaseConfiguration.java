package com.github.krpkbhdn.ccmanager.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfiguration.class);
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    @Bean
    DataSource dataSource(AppProperties properties, DatabaseProperties database) throws IOException {
        createOwnerOnly(properties.dataDir());
        return create(properties.dataDir().resolve("cc-manager.db"), database);
    }

    /** The data dir holds the database, browser cookies and screenshots; other local users must not read it. */
    static void createOwnerOnly(Path dir) throws IOException {
        // Windows has no POSIX view: the user-profile ACLs are already owner-only.
        if (!dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectories(dir);
            return;
        }
        try {
            Files.createDirectories(dir, OWNER_ONLY);
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            log.warn("Could not restrict {} to its owner: {}", dir, e.getMessage());
            Files.createDirectories(dir);
        }
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
