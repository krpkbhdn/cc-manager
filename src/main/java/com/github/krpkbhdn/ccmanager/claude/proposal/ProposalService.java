package com.github.krpkbhdn.ccmanager.claude.proposal;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookProperties;
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Proposals whose hook call is held open, so the app can answer instead of the terminal dialog. A held call ends with the answer's
 * hook output, or with an empty body (dismissed, settled in the terminal, interrupted, timed out) that leaves the dialog to the terminal.
 * Thread-safe.
 */
@Component
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final Map<String, Held> held = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final SessionRegistry sessions;
    private final HookProperties properties;

    public ProposalService(SessionRegistry sessions, HookProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
        sessions.subscribe(this::releaseOfEndedOrInterruptedSessions);
    }

    /**
     * Registers the proposal behind a PermissionRequest or Elicitation event, in event order.
     * @return completes with the hook output to send back, or empty for an empty 200; already empty for any other event
     */
    public CompletableFuture<Optional<String>> hold(HookEvent event) {
        var proposal = ProposalParser.parse(event);
        if (proposal.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var decision = new CompletableFuture<Optional<String>>()
                .completeOnTimeout(Optional.empty(), properties.decisionHold().toMillis(), TimeUnit.MILLISECONDS);
        var id = proposal.get().id();
        held.put(id, new Held(proposal.get(), decision));
        decision.whenComplete((output, error) -> {
            held.remove(id);
            notifyListeners();
        });
        notifyListeners();
        return decision;
    }

    public List<Proposal> pending() {
        return held.values().stream().map(Held::proposal).sorted(Comparator.comparing(Proposal::createdAt)).toList();
    }

    public List<Proposal> pending(String ccmSessionId) {
        return pending().stream().filter(proposal -> proposal.ccmSessionId().equals(ccmSessionId)).toList();
    }

    /**
     * @return false when the proposal is gone already (settled elsewhere or timed out)
     * @throws IllegalArgumentException when the answer is of another kind than the proposal
     */
    public boolean answer(String proposalId, ProposalAnswer answer) {
        var pending = held.get(proposalId);
        if (pending == null) {
            return false;
        }
        if (!answer.fits(pending.proposal())) {
            throw new IllegalArgumentException("Answer does not fit proposal " + proposalId);
        }
        return pending.decision().complete(Optional.of(answer.hookOutput(pending.proposal())));
    }

    /** Hides the proposal from the app; the held call gets an empty body, so the terminal dialog keeps working. */
    public boolean dismiss(String proposalId) {
        var pending = held.get(proposalId);
        return pending != null && pending.decision().complete(Optional.empty());
    }

    /** @return unsubscribe handle; callback fires (any thread) after a proposal appears or goes */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    @EventListener
    public void on(HookEvent event) {
        held.values().stream()
                .filter(pending -> pending.proposal().ccmSessionId().equals(event.ccmSessionId()) && pending.proposal().settledBy(event))
                .forEach(pending -> pending.decision().complete(Optional.empty()));
    }

    /** Esc and process exit emit no hook event; an idle or gone session cannot be waiting for a decision any more. */
    private void releaseOfEndedOrInterruptedSessions() {
        held.values().stream()
                .filter(pending -> sessions.find(pending.proposal().ccmSessionId()).map(session -> session.status() == AgentStatus.IDLE).orElse(true))
                .forEach(pending -> pending.decision().complete(Optional.empty()));
    }

    private void notifyListeners() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("Proposal listener failed", e);
            }
        }
    }

    private record Held(Proposal proposal, CompletableFuture<Optional<String>> decision) {}
}
