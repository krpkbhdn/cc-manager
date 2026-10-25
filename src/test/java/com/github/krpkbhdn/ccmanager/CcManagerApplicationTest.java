package com.github.krpkbhdn.ccmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.notification.NotificationSoundService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

class CcManagerApplicationTest {

    @TempDir
    Path dataDir;

    @Test
    void contextStartsAndMigratesSqliteInWalMode() {
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var jdbc = context.getBean(JdbcClient.class);

            assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualTo("wal");
            assertThat(jdbc.sql("SELECT count(*) FROM project").query(Integer.class).single()).isZero();
        }
    }

    @Test
    void notificationSoundServiceIsABeanAndEnabledByDefault() {
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            assertThat(context.getBean(NotificationSoundService.class).enabled()).isTrue();
        }
    }
}
