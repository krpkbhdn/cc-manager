package com.github.krpkbhdn.ccmanager.claude.hook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of the hook pipeline: rows kept in hook_event, largest accepted request body, stored string length,
 * and how long a permission or elicitation call is held for an answer (a bit under the 600 s hook timeout in the settings file).
 */
@ConfigurationProperties("cc-manager.hooks")
public record HookProperties(
        @DefaultValue("50000") int retention,
        @DefaultValue("33554432") int maxBodyBytes,
        @DefaultValue("500") int maxStoredTextLength,
        @DefaultValue("590s") Duration decisionHold) {}
