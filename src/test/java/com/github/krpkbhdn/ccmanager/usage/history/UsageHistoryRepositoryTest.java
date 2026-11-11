package com.github.krpkbhdn.ccmanager.usage.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class UsageHistoryRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

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

    private static Instant noon(LocalDate day) {
        return day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private void sample(LocalDate day, int hourOffset, String claude, long project, String model, double cost,
            double delta) {
        repository.insertSample(new UsageSample(noon(day).plusSeconds(hourOffset * 3600L), "ccm-" + claude, claude,
                project, model, "high", 12.5, cost, delta));
    }

    @Test
    void lastCostIsTheLatestCumulativeCostOfTheSession() {
        sample(DAY, 0, "s1", 1, "opus", 1.0, 1.0);
        sample(DAY, 1, "s1", 1, "opus", 2.5, 1.5);
        sample(DAY, 2, "s2", 1, "opus", 9.0, 9.0);

        assertThat(repository.lastCost("s1")).hasValue(2.5);
        assertThat(repository.lastCost("none")).isEmpty();
    }

    @Test
    void dailyCostsSumDeltasPerDayProjectAndModel() {
        sample(DAY, 0, "s1", 1, "opus", 1.0, 1.0);
        sample(DAY, 1, "s1", 1, "opus", 2.5, 1.5);
        sample(DAY.plusDays(1), 0, "s1", 1, "opus", 3.0, 0.5);

        var costs = repository.dailyCosts(startOf(DAY), startOf(DAY.plusDays(2)));

        assertThat(costs).hasSize(2);
        assertThat(costs.get(0).day()).isEqualTo(DAY);
        assertThat(costs.get(0).costUsd()).isCloseTo(2.5, within(1e-9));
        assertThat(costs.get(1).costUsd()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void topSessionsAreOrderedByCostAndLimited() {
        sample(DAY, 0, "cheap", 1, "opus", 1.0, 1.0);
        sample(DAY, 0, "dear", 1, "opus", 1.0, 1.0);
        sample(DAY, 1, "dear", 1, "opus", 5.0, 4.0);
        sample(DAY, 0, "mid", 2, "sonnet", 2.0, 2.0);

        var top = repository.topSessions(startOf(DAY), startOf(DAY.plusDays(1)), 2);

        assertThat(top).extracting(SessionCost::claudeSessionId).containsExactly("dear", "mid");
        assertThat(top.get(0).costUsd()).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void alertFiresOnlyOncePerKey() {
        assertThat(repository.markAlertFired("budget-day", 1L)).isTrue();
        assertThat(repository.markAlertFired("budget-day", 2L)).isFalse();
    }

    @Test
    void rateLimitSampleAcceptsMissingWindows() {
        repository.insertRateLimit(new RateLimitSample(noon(DAY), null, null, 40.0, noon(DAY).plusSeconds(60)));
    }

    @Test
    void aggregateBeforeKeepsTotalsAndDeletesRawRows() {
        sample(DAY, 0, "s1", 1, "opus", 1.0, 1.0);
        sample(DAY, 1, "s2", 1, "opus", 2.0, 2.0);
        var from = startOf(DAY);
        var to = startOf(DAY.plusDays(1));
        var before = repository.summary(from, to);

        repository.aggregateBefore(noon(DAY.plusDays(1)).toEpochMilli());
        sample(DAY, 4, "s3", 1, "opus", 4.0, 4.0);

        var after = repository.summary(from, to);
        assertThat(after.totalCostUsd()).isCloseTo(before.totalCostUsd() + 4.0, within(1e-9));
        assertThat(after.sampleCount()).isEqualTo(3);
        assertThat(repository.costOn(DAY)).isCloseTo(7.0, within(1e-9));
        assertThat(repository.dailyCosts(from, to)).singleElement()
                .satisfies(c -> assertThat(c.costUsd()).isCloseTo(7.0, within(1e-9)));
        assertThat(repository.lastCost("s1")).hasValue(1.0);
    }

    @Test
    void costBaselineSurvivesAggregationSoAResumedSessionIsNotCountedTwice() {
        sample(DAY, 0, "s1", 1, "opus", 4.0, 4.0);
        sample(DAY, 1, "s1", 1, "opus", 10.0, 6.0);
        var from = startOf(DAY);
        var to = startOf(DAY.plusDays(1));

        repository.aggregateBefore(noon(DAY.plusDays(1)).toEpochMilli());
        repository.aggregateBefore(noon(DAY.plusDays(2)).toEpochMilli());

        assertThat(repository.lastCost("s1")).hasValue(10.0);
        assertThat(repository.summary(from, to).totalCostUsd()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void aggregateBeforeMergesADayOnceWhenTheCutoffMovesWithinTheNextDay() {
        sample(DAY, 0, "s1", 1, "opus", 1.0, 1.0);
        sample(DAY, 1, "s2", 1, "opus", 2.0, 2.0);
        var from = startOf(DAY);
        var to = startOf(DAY.plusDays(1));
        var before = repository.summary(from, to);

        repository.aggregateBefore(startOf(DAY.plusDays(1)).plusSeconds(3600).toEpochMilli());
        repository.aggregateBefore(startOf(DAY.plusDays(1)).plusSeconds(7 * 3600L).toEpochMilli());
        repository.aggregateBefore(startOf(DAY.plusDays(1)).plusSeconds(13 * 3600L).toEpochMilli());

        assertThat(repository.summary(from, to)).isEqualTo(before);
    }

    @Test
    void costOnCountsOnlyTheLocalDay() {
        sample(DAY.minusDays(1), 11, "s1", 1, "opus", 1.0, 1.0);
        sample(DAY, 0, "s1", 1, "opus", 3.0, 2.0);
        sample(DAY.plusDays(1), 0, "s1", 1, "opus", 4.0, 1.0);

        assertThat(repository.costOn(DAY)).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void foldedDayIsCountedOnlyWhenWhollyInsideTheRange() {
        sample(DAY, 0, "s1", 1, "opus", 1.0, 1.0);
        repository.aggregateBefore(startOf(DAY.plusDays(1)).toEpochMilli());

        assertThat(repository.summary(noon(DAY).plusSeconds(7200), startOf(DAY.plusDays(3))).totalCostUsd()).isZero();
        assertThat(repository.summary(startOf(DAY.minusDays(1)), noon(DAY)).totalCostUsd()).isZero();
        assertThat(repository.summary(startOf(DAY), startOf(DAY.plusDays(1))).totalCostUsd())
                .isCloseTo(1.0, within(1e-9));
        assertThat(repository.dailyCosts(noon(DAY).plusSeconds(7200), startOf(DAY.plusDays(3)))).isEmpty();
    }

    @Test
    void aggregateBeforeKeepsRowsOfTheCutoffDay() {
        sample(DAY, 0, "s1", 1, "opus", 1.0, 1.0);

        repository.aggregateBefore(noon(DAY).plusSeconds(3600).toEpochMilli());

        assertThat(repository.topSessions(startOf(DAY), startOf(DAY.plusDays(1)), 10)).hasSize(1);
    }
}
