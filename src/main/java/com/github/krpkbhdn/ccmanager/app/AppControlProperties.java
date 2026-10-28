package com.github.krpkbhdn.ccmanager.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of app control: how long a tool waits for the UI to carry out an action. */
@ConfigurationProperties("cc-manager.app-control")
public record AppControlProperties(@DefaultValue("10s") Duration uiTimeout) {}
