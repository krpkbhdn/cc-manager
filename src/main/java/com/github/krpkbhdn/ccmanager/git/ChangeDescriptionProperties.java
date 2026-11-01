package com.github.krpkbhdn.ccmanager.git;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings of the claude one-shot that describes changes and of the git reads feeding it. */
@ConfigurationProperties("cc-manager.git.description")
public record ChangeDescriptionProperties(
        @DefaultValue("haiku") String model,
        @DefaultValue("120s") Duration timeout,
        @DefaultValue("15s") Duration gitTimeout,
        @DefaultValue("30000") int contextChars,
        @DefaultValue("4000") int untrackedFileChars) {}
