package com.github.krpkbhdn.ccmanager.usage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Usage history tunables: raw sample retention and the default alert thresholds (0 turns an alert off), and how often an unchanged rate limit is sampled. */
@ConfigurationProperties("cc-manager.usage")
public record UsageProperties(
        @DefaultValue("90d") Duration rawRetention,
        @DefaultValue("6h") Duration retentionInterval,
        @DefaultValue("80") int fiveHourPercent,
        @DefaultValue("80") int sevenDayPercent,
        @DefaultValue("0") double dailyBudgetUsd,
        @DefaultValue("5m") Duration rateLimitSampleInterval) {}
