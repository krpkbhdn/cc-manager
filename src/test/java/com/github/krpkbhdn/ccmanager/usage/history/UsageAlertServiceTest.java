package com.github.krpkbhdn.ccmanager.usage.history;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.notification.NotificationKind;
import com.github.krpkbhdn.ccmanager.notification.NotificationService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot;
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow;
import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageAlertServiceTest {

    private static final Instant RESET = Instant.parse("2030-01-01T10:00:00Z");

    private final UsageHistoryRepository repository = mock(UsageHistoryRepository.class);
    private final RateLimitService rateLimits = mock(RateLimitService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Set<String> fired = new HashSet<>();

    @BeforeEach
    void setUp() {
        when(repository.markAlertFired(anyString(), anyLong())).thenAnswer(call -> fired.add(call.getArgument(0)));
    }

    private UsageAlertService service(double budget) {
        var properties = new UsageProperties(Duration.ofDays(90), Duration.ofHours(6), 80, 0, budget, Duration.ofMinutes(5));
        return new UsageAlertService(properties, repository, rateLimits, notifications);
    }

    private void fiveHour(double percent, Instant resetsAt) {
        var snapshot = new RateLimitSnapshot(new RateLimitWindow(percent, resetsAt), null, Instant.now());
        when(rateLimits.current()).thenReturn(Optional.of(snapshot));
    }

    private void verifyPosts(int times) {
        verify(notifications, times(times)).post(eq(NotificationKind.USAGE_ALERT), eq(0L), any(), anyString(), anyString());
    }

    @Test
    void crossingTheThresholdAlertsOncePerWindow() {
        var service = service(0);
        fiveHour(79, RESET);
        service.evaluateRateLimits();
        fiveHour(81, RESET);
        service.evaluateRateLimits();
        fiveHour(85, RESET);
        service.evaluateRateLimits();

        verifyPosts(1);
    }

    @Test
    void repeatedEvaluationOverThresholdHitsTheRepositoryOnce() {
        var service = service(0);
        fiveHour(90, RESET);
        service.evaluateRateLimits();
        service.evaluateRateLimits();
        service.evaluateRateLimits();

        verify(repository, times(1)).markAlertFired(anyString(), anyLong());
    }

    @Test
    void newResetTimeAlertsAgain() {
        var service = service(0);
        fiveHour(90, RESET);
        service.evaluateRateLimits();
        fiveHour(90, RESET.plus(Duration.ofHours(5)));
        service.evaluateRateLimits();

        verifyPosts(2);
    }

    @Test
    void restartDoesNotAlertAgain() {
        fiveHour(90, RESET);
        service(0).evaluateRateLimits();
        service(0).evaluateRateLimits();

        verifyPosts(1);
    }

    @Test
    void noSnapshotMeansNoAlert() {
        when(rateLimits.current()).thenReturn(Optional.empty());
        service(0).evaluateRateLimits();

        verifyPosts(0);
    }

    @Test
    void disabledSevenDayThresholdNeverAlerts() {
        var snapshot = new RateLimitSnapshot(null, new RateLimitWindow(99, RESET), Instant.now());
        when(rateLimits.current()).thenReturn(Optional.of(snapshot));
        service(0).evaluateRateLimits();

        verifyPosts(0);
    }

    @Test
    void dailyBudgetAlertsOncePerDay() {
        when(repository.costOn(any())).thenReturn(12.0);
        var service = service(10);
        service.evaluateDailyBudget();
        service.evaluateDailyBudget();

        verifyPosts(1);
    }

    @Test
    void budgetAboveCostOrOffDoesNotAlert() {
        when(repository.costOn(any())).thenReturn(5.0);
        service(10).evaluateDailyBudget();
        service(0).evaluateDailyBudget();

        verify(notifications, never()).post(any(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    void configureReplacesThresholds() {
        when(repository.costOn(any())).thenReturn(5.0);
        var service = service(0);
        service.configure(new UsageThresholds(0, 0, 4));

        verifyPosts(1);
    }
}
