package com.github.krpkbhdn.ccmanager.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Notification sound tunables; volume is 0.0 (silent) to 1.0 (full), bursts closer than minInterval are coalesced, the output line stays open lineTail after the sound so devices do not cut the end. */
@ConfigurationProperties("cc-manager.notification-sound")
public record NotificationSoundProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1.0") double volume,
        @DefaultValue("1s") Duration minInterval,
        @DefaultValue("500ms") Duration lineTail) {}
