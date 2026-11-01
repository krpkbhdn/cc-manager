package com.github.krpkbhdn.ccmanager.usage;

import com.github.krpkbhdn.ccmanager.claude.hook.StatusLineReport;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Subscription limits are account-wide, so one snapshot serves all sessions: the newest report wins. Thread-safe. */
@Component
public class RateLimitService {

    private final AtomicReference<RateLimitSnapshot> latest = new AtomicReference<>();
    private final ChangeListeners listeners = new ChangeListeners("Rate limit");

    @EventListener
    public void on(StatusLineReport report) {
        RateLimitParser.parse(report.payload(), report.receivedAt()).ifPresent(this::offer);
    }

    public Optional<RateLimitSnapshot> current() {
        return Optional.ofNullable(latest.get());
    }

    /** @return unsubscribe handle; callback fires (any thread) when a newer snapshot arrived */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    private void offer(RateLimitSnapshot snapshot) {
        var kept = latest.accumulateAndGet(snapshot, (old, candidate) ->
                old == null || !candidate.receivedAt().isBefore(old.receivedAt()) ? candidate : old);
        if (kept == snapshot) {
            listeners.fire();
        }
    }
}
