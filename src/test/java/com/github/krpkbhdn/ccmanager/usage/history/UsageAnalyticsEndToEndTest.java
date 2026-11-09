package com.github.krpkbhdn.ccmanager.usage.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.notification.NotificationKind;
import com.github.krpkbhdn.ccmanager.notification.NotificationProperties;
import com.github.krpkbhdn.ccmanager.notification.NotificationService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot;
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow;
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService;
import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class UsageAnalyticsEndToEndTest {

    private final SessionRegistry registry = mock(SessionRegistry.class);
    private final SessionUsageService usage = mock(SessionUsageService.class);
    private final RateLimitService rateLimits = mock(RateLimitService.class);
    private final UsageProperties properties =
            new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5));
    private final Instant resetsAt = Instant.now().plus(Duration.ofHours(3));
    private UsageHistoryRepository repository;
    private NotificationService notifications;
    private UsageRecorder recorder;
    private Runnable rateLimitChanged;

    @BeforeEach
    void setUp(@TempDir Path temp) {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("e2e.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new UsageHistoryRepository(JdbcClient.create(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        notifications = new NotificationService(registry, new NotificationProperties(200, java.time.Duration.ofSeconds(2)));
        when(rateLimits.subscribe(any())).thenAnswer(call -> {
            if (rateLimitChanged == null) {
                rateLimitChanged = call.getArgument(0);
            }
            return (Runnable) () -> { };
        });
        var alerts = new UsageAlertService(properties, repository, rateLimits, notifications);
        recorder = new UsageRecorder(registry, usage, rateLimits, repository, alerts, properties, Runnable::run, java.time.Clock.systemUTC());
    }

    private void fiveHour(double percent) {
        var snapshot = new RateLimitSnapshot(new RateLimitWindow(percent, resetsAt), null, Instant.now());
        when(rateLimits.current()).thenReturn(Optional.of(snapshot));
    }

    private long usageAlerts() {
        return notifications.history().stream().filter(n -> n.kind() == NotificationKind.USAGE_ALERT).count();
    }

    @Test
    void oneAlertForSeventyNineEightyOneEightyFivePercentAndNoneAfterRestart() {
        for (var percent : new double[] {79, 81, 85}) {
            fiveHour(percent);
            rateLimitChanged.run();
        }
        assertThat(usageAlerts()).isEqualTo(1);

        var restartedNotifications = new NotificationService(registry, new NotificationProperties(200, java.time.Duration.ofSeconds(2)));
        new UsageAlertService(properties, repository, rateLimits, restartedNotifications).evaluateRateLimits();
        assertThat(restartedNotifications.history()).isEmpty();
    }

    @Test
    void cumulativeCostIsStoredAsDeltasAndResetStartsNewBaseline() {
        var now = Instant.now();
        for (var cost : new double[] {1.0, 2.5, 2.5, 0.4}) {
            recorder.write(new UsageSample(now, "ccm-1", "claude-1", 1L, "opus", "high", 10.0, cost, 0));
        }

        var total = repository.dailyCosts(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))).stream()
                .mapToDouble(DailyCost::costUsd).sum();
        assertThat(total).isCloseTo(1.0 + 1.5 + 0 + 0.4, within(1e-9));
    }
}
