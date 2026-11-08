package com.github.krpkbhdn.ccmanager.usage.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class UsageRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final UsageProperties PROPERTIES =
            new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 80, 0, Duration.ofMinutes(5));

    @TempDir
    Path temp;

    UsageHistoryRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new UsageHistoryRepository(JdbcClient.create(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private void sample(Instant at, String session, double delta) {
        repository.insertSample(new UsageSample(at, "ccm-" + session, session, 1, "opus", "high", 10.0, delta, delta));
    }

    @Test
    void oldSamplesMoveIntoDailyTotalsWithoutChangingTheSums() {
        var old = NOW.minus(Duration.ofDays(120));
        var recent = NOW.minus(Duration.ofDays(1));
        sample(old, "s1", 1.5);
        sample(old.plusSeconds(60), "s1", 2.0);
        sample(recent, "s2", 4.0);
        var from = NOW.minus(Duration.ofDays(200));
        var to = NOW.plusSeconds(1);
        var costBefore = repository.dailyCosts(from, to).stream().mapToDouble(DailyCost::costUsd).sum();
        var before = repository.summary(from, to);

        new UsageRetentionService(repository, PROPERTIES).runOnce(NOW);

        var after = repository.summary(from, to);
        assertThat(repository.dailyCosts(from, to).stream().mapToDouble(DailyCost::costUsd).sum())
                .isCloseTo(costBefore, within(1e-9));
        assertThat(after.totalCostUsd()).isCloseTo(before.totalCostUsd(), within(1e-9));
        assertThat(after.sampleCount()).isEqualTo(before.sampleCount());
        assertThat(repository.summary(NOW.minus(Duration.ofDays(2)), to).sampleCount()).isEqualTo(1);
        assertThat(repository.topSessions(from, to, 10)).extracting(SessionCost::claudeSessionId)
                .containsExactly("s2", "s1");
    }

    @Test
    void repositoryFailureDoesNotPropagate() {
        var failing = mock(UsageHistoryRepository.class);
        doThrow(new IllegalStateException("db")).when(failing).aggregateBefore(anyLong());

        assertThatCode(() -> new UsageRetentionService(failing, PROPERTIES).runOnce(NOW)).doesNotThrowAnyException();
    }
}
