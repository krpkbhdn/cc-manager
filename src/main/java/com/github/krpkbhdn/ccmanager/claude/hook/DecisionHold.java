package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Holds a PermissionRequest or Elicitation call until the user answers, bounded by the decision hold. */
final class DecisionHold {

    private static final Logger log = LoggerFactory.getLogger(DecisionHold.class);

    private final DecisionHolder proposals;
    private final HookProperties properties;

    DecisionHold(DecisionHolder proposals, HookProperties properties) {
        this.proposals = proposals;
        this.properties = properties;
    }

    /**
     * The event is accepted (stored and published) on the worker first, so its order and the NEEDS_ACTION status are as for any hook; the worker never waits.
     * An empty output leaves the terminal dialog in charge.
     */
    CompletableFuture<Optional<String>> hold(OrderedWorker worker, String ccmSessionId, Supplier<HookEvent> accept, Runnable onRejected) {
        var decision = new CompletableFuture<Optional<String>>();
        Runnable hold = () -> {
            try {
                proposals.hold(accept.get()).whenComplete((output, error) -> decision.complete(output == null ? Optional.empty() : output));
            } catch (RuntimeException e) {
                log.warn("Could not hold hook call for session {}: {}", ccmSessionId, e.toString());
                decision.complete(Optional.empty());
            }
        };
        try {
            worker.execute(hold);
        } catch (RejectedExecutionException e) {
            onRejected.run();
            decision.complete(Optional.empty());
        }
        // a hold task dropped by stop() would never complete the future, so the wait is bounded here too
        return decision.completeOnTimeout(Optional.empty(), properties.decisionHold().toMillis(), TimeUnit.MILLISECONDS);
    }
}
