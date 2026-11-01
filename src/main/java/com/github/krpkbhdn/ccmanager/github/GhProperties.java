package com.github.krpkbhdn.ccmanager.github;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings of the GitHub CLI calls. */
@ConfigurationProperties("cc-manager.github")
public record GhProperties(
        @DefaultValue("gh") String executable,
        @DefaultValue("60s") Duration timeout,
        @DefaultValue("20s") Duration ciPollInterval,
        @DefaultValue("10m") Duration readyTtl) {}
