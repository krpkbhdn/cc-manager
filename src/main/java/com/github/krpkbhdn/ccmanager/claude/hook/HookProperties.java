package com.github.krpkbhdn.ccmanager.claude.hook;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of the hook pipeline: rows kept in hook_event, largest accepted request body, stored string length,
 * how long a permission or elicitation call is held for an answer (the hook timeout in the settings file is this plus 10 s),
 * how many calls may be held at once (beyond it the terminal dialog stays in charge) and how many rows an activity query scans.
 */
@ConfigurationProperties("cc-manager.hooks")
public record HookProperties(
        @DefaultValue("50000") int retention,
        @DefaultValue("33554432") int maxBodyBytes,
        @DefaultValue("500") int maxStoredTextLength,
        @DefaultValue("590s") Duration decisionHold,
        @DefaultValue("50") int maxHeldProposals,
        @DefaultValue("2000") int maxScannedEvents) {

    public HookProperties {
        positive("cc-manager.hooks.retention", retention);
        positive("cc-manager.hooks.max-body-bytes", maxBodyBytes);
        positive("cc-manager.hooks.max-stored-text-length", maxStoredTextLength);
        positive("cc-manager.hooks.decision-hold", decisionHold);
        nonNegative("cc-manager.hooks.max-held-proposals", maxHeldProposals);
        positive("cc-manager.hooks.max-scanned-events", maxScannedEvents);
    }
}
