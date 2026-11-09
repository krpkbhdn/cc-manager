package com.github.krpkbhdn.ccmanager.claude.hook;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of the hook pipeline: rows kept in hook_event, largest accepted request body, stored string length,
 * how long a permission or elicitation call is held for an answer (the hook timeout in the settings file is this plus 10 s),
 * how many calls may be held at once (beyond it the terminal dialog stays in charge), how many rows an activity query scans
 * and how many hook calls (and event writes) may wait for the single worker before the newest are dropped.
 */
@ConfigurationProperties("cc-manager.hooks")
public record HookProperties(
        @DefaultValue("50000") int retention,
        @DefaultValue("4194304") int maxBodyBytes,
        @DefaultValue("500") int maxStoredTextLength,
        @DefaultValue("590s") Duration decisionHold,
        @DefaultValue("50") int maxHeldProposals,
        @DefaultValue("2000") int maxScannedEvents,
        @DefaultValue("5000") int maxQueuedCalls) {

    @ConstructorBinding
    public HookProperties {
        positive("cc-manager.hooks.retention", retention);
        positive("cc-manager.hooks.max-body-bytes", maxBodyBytes);
        positive("cc-manager.hooks.max-stored-text-length", maxStoredTextLength);
        positive("cc-manager.hooks.decision-hold", decisionHold);
        nonNegative("cc-manager.hooks.max-held-proposals", maxHeldProposals);
        positive("cc-manager.hooks.max-scanned-events", maxScannedEvents);
        positive("cc-manager.hooks.max-queued-calls", maxQueuedCalls);
    }
}
