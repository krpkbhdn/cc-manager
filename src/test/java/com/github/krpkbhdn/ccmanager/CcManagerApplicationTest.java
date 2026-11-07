package com.github.krpkbhdn.ccmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.notification.NotificationSoundService;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

class CcManagerApplicationTest {

    @TempDir
    static Path dataDir;

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startContext() {
        context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir});
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void contextStartsAndMigratesSqliteInWalMode() {
        var jdbc = context.getBean(JdbcClient.class);

        assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualTo("wal");
        assertThat(jdbc.sql("SELECT count(*) FROM project").query(Integer.class).single()).isZero();
    }

    @Test
    void notificationSoundServiceIsABeanAndEnabledByDefault() {
        assertThat(context.getBean(NotificationSoundService.class).enabled()).isTrue();
    }
}
