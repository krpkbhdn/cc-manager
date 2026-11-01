package com.github.krpkbhdn.ccmanager.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of app control: how long a tool waits for the UI, and the most characters of a skill or prompt text the Assistant's read tools return in one call. */
@ConfigurationProperties("cc-manager.app-control")
public record AppControlProperties(@DefaultValue("10s") Duration uiTimeout, @DefaultValue("20000") int maxTextLength) {}
