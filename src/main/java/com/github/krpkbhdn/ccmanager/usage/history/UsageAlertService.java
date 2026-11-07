package com.github.krpkbhdn.ccmanager.usage.history;

import com.github.krpkbhdn.ccmanager.notification.NotificationKind;
import com.github.krpkbhdn.ccmanager.notification.NotificationService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitService;
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow;
import com.github.krpkbhdn.ccmanager.usage.UsageProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Posts one notification per crossed limit window or budget day; the fired keys are persisted, so a restart stays quiet. */
@Service
@EnableConfigurationProperties(UsageProperties.class)
public class UsageAlertService {

    private static final long NO_RESET = -1;
    private static final long ASSISTANT_PROJECT = 0;
    private static final int MAX_SEEN_KEYS = 256;

    private static final Logger log = LoggerFactory.getLogger(UsageAlertService.class);

    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final UsageHistoryRepository repository;
    private final RateLimitService rateLimits;
    private final NotificationService notifications;
    private volatile UsageThresholds thresholds;

    public UsageAlertService(UsageProperties properties, UsageHistoryRepository repository, RateLimitService rateLimits,
            NotificationService notifications) {
        this.repository = repository;
        this.rateLimits = rateLimits;
        this.notifications = notifications;
        this.thresholds = new UsageThresholds(properties.fiveHourPercent(), properties.sevenDayPercent(),
                properties.dailyBudgetUsd());
        rateLimits.subscribe(this::evaluateRateLimits);
    }

    public void configure(UsageThresholds next) {
        thresholds = next;
        evaluateRateLimits();
        evaluateDailyBudget();
    }

    public void evaluateRateLimits() {
        rateLimits.current().ifPresent(snapshot -> {
            alertWindow("FIVE_HOUR", "5-hour", snapshot.fiveHour(), thresholds.fiveHourPercent());
            alertWindow("SEVEN_DAY", "7-day", snapshot.sevenDay(), thresholds.sevenDayPercent());
        });
    }

    public void evaluateDailyBudget() {
        var budget = thresholds.dailyBudgetUsd();
        var today = LocalDate.now();
        if (budget <= 0 || repository.costOn(today) < budget) {
            return;
        }
        fire("DAILY_COST:" + today + ":" + budget, "Daily budget reached",
                String.format(Locale.ROOT, "Today's Claude cost reached $%.2f.", budget));
    }

    private void alertWindow(String name, String label, RateLimitWindow window, int threshold) {
        if (window == null || threshold <= 0 || window.usedPercent() < threshold
                || window.resetsAt() != null && !window.resetsAt().isAfter(Instant.now())) {
            return;
        }
        fire(name + ":" + resetMinute(window.resetsAt()) + ":" + threshold, "Usage limit: " + label + " window",
                String.format(Locale.ROOT, "%.0f%% of the %s allowance is used.", window.usedPercent(), label));
    }

    private void fire(String key, String title, String body) {
        if (seen.contains(key)) {
            return;
        }
        var recorded = repository.markAlertFired(key, System.currentTimeMillis());
        if (seen.size() >= MAX_SEEN_KEYS) {
            seen.clear(); // the database still dedupes, so a cleared key only costs one more insert attempt
        }
        seen.add(key);
        if (recorded) {
            try {
                notifications.post(NotificationKind.USAGE_ALERT, ASSISTANT_PROJECT, null, title, body);
            } catch (RuntimeException e) {
                log.warn("Usage alert notification failed: {}", e.getClass().getSimpleName());
            }
        }
    }

    private static long resetMinute(Instant resetsAt) {
        return resetsAt == null ? NO_RESET : resetsAt.truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
    }
}
