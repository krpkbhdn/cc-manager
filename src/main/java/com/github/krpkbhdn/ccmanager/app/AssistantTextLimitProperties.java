package com.github.krpkbhdn.ccmanager.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of the Assistant's read tools: the most characters of a skill or prompt text returned in one call. */
@ConfigurationProperties("cc-manager.app-control")
public record AssistantTextLimitProperties(@DefaultValue("20000") int maxTextLength) {}
