package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Duration;
import java.time.Instant;

/**
 * One subagent of a session. activity is the tool being run (empty while the agent is thinking); lastMessage and
 * transcriptPath arrive with SubagentStop. Background agents outlive the turn that launched them. stale is set when a
 * running agent has been silent for too long: it may never get a SubagentStop, so it is dimmed instead of closed.
 */
public record SubagentSnapshot(
        String agentId,
        String agentType,
        String activity,
        SubagentState state,
        boolean background,
        int toolCalls,
        boolean lastToolFailed,
        String lastMessage,
        String transcriptPath,
        Instant startedAt,
        Instant lastEventAt,
        Instant endedAt,
        boolean stale) {

    static SubagentSnapshot started(String agentId, String agentType, boolean background, String transcript, Instant at) {
        return new SubagentSnapshot(agentId, agentType, "", SubagentState.WORKING, background, 0, false, "", transcript, at, at, null, false);
    }

    public boolean running() {
        return state.live();
    }

    public Duration silentFor(Instant now) {
        return Duration.between(lastEventAt, now);
    }

    SubagentSnapshot inBackground() {
        return new SubagentSnapshot(agentId, agentType, activity, state, true, toolCalls, lastToolFailed, lastMessage, transcriptPath,
                startedAt, lastEventAt, endedAt, stale);
    }

    SubagentSnapshot toolStarted(String text, Instant at) {
        return new SubagentSnapshot(agentId, agentType, text, SubagentState.WORKING, background, toolCalls + 1, false, lastMessage,
                transcriptPath, startedAt, at, endedAt, false);
    }

    SubagentSnapshot toolFinished(boolean failed, Instant at) {
        return new SubagentSnapshot(agentId, agentType, "", SubagentState.WORKING, background, toolCalls, failed, lastMessage,
                transcriptPath, startedAt, at, endedAt, false);
    }

    SubagentSnapshot waiting(String text, Instant at) {
        return new SubagentSnapshot(agentId, agentType, text, SubagentState.NEEDS_ACTION, background, toolCalls, lastToolFailed,
                lastMessage, transcriptPath, startedAt, at, endedAt, false);
    }

    SubagentSnapshot finished(SubagentState finalState, String message, String transcript, Instant at) {
        return new SubagentSnapshot(agentId, agentType, "", finalState, background, toolCalls, lastToolFailed, message, transcript,
                startedAt, at, at, false);
    }

    /** A zero or negative [staleAfter] disables staleness. */
    SubagentSnapshot withStale(Instant now, Duration staleAfter) {
        var silent = running() && !staleAfter.isZero() && !staleAfter.isNegative() && silentFor(now).compareTo(staleAfter) > 0;
        return silent == stale ? this : new SubagentSnapshot(agentId, agentType, activity, state, background, toolCalls, lastToolFailed,
                lastMessage, transcriptPath, startedAt, lastEventAt, endedAt, silent);
    }
}
