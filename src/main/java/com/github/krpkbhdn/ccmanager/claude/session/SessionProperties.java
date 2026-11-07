package com.github.krpkbhdn.ccmanager.claude.session;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of session tracking: how long a running subagent may stay silent before it is shown as inactive, and how
 * long a working session may stay silent before the all-sessions screen flags it. Zero disables the check.
 */
@ConfigurationProperties("cc-manager.sessions")
public record SessionProperties(@DefaultValue("10m") Duration subagentStaleAfter, @DefaultValue("10m") Duration workingStaleAfter) {

    public SessionProperties {
        nonNegative("cc-manager.sessions.subagent-stale-after", subagentStaleAfter);
        nonNegative("cc-manager.sessions.working-stale-after", workingStaleAfter);
    }
}
