package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable state of one live session; the status is derived from the flags, never stored.
 * Empty strings mean "unknown". The title is the user-given name; the generated topic shows only without one.
 */
record SessionState(
        String ccmSessionId,
        long projectId,
        String claudeSessionId,
        String activity,
        String permissionMode,
        String model,
        String title,
        String topic,
        Instant startedAt,
        Instant lastEventAt,
        Map<String, SubagentSnapshot> subagents,
        int backgroundTasks,
        String error,
        boolean turnOpen,
        boolean compacting,
        boolean needsAction,
        Set<String> backgroundAgentIds) {

    private static final int MAX_SUBAGENTS = 50;

    /** Mutable copy used to build the next state in one step. */
    static final class Draft {
        String claudeSessionId;
        String activity;
        String permissionMode;
        String model;
        String title;
        Instant lastEventAt;
        Map<String, SubagentSnapshot> subagents;
        int backgroundTasks;
        String error;
        boolean turnOpen;
        boolean compacting;
        boolean needsAction;
        Set<String> backgroundAgentIds;

        private Draft(SessionState s) {
            claudeSessionId = s.claudeSessionId;
            activity = s.activity;
            permissionMode = s.permissionMode;
            model = s.model;
            title = s.title;
            lastEventAt = s.lastEventAt;
            subagents = s.subagents;
            backgroundTasks = s.backgroundTasks;
            error = s.error;
            turnOpen = s.turnOpen;
            compacting = s.compacting;
            needsAction = s.needsAction;
            backgroundAgentIds = s.backgroundAgentIds;
        }

        /** Keeps the current value when the new one is empty. */
        void rememberMetadata(String newPermissionMode, String newModel, String newTitle) {
            permissionMode = newPermissionMode.isEmpty() ? permissionMode : newPermissionMode;
            model = newModel.isEmpty() ? model : newModel;
            title = newTitle.isEmpty() ? title : newTitle;
        }

        /** Waiting for the user. */
        void requireAction() {
            needsAction = true;
        }

        void resolveAction() {
            needsAction = false;
        }

        /** The launch call named this agent as a background one; its SubagentStart may come before or after that. */
        void markBackground(String agentId) {
            var agent = subagents.get(agentId);
            if (agent == null) {
                var ids = new java.util.HashSet<>(backgroundAgentIds);
                ids.add(agentId);
                backgroundAgentIds = ids;
                return;
            }
            var copy = new LinkedHashMap<>(subagents);
            copy.put(agentId, agent.inBackground());
            subagents = copy;
        }

        boolean takeBackground(String agentId) {
            var ids = new java.util.HashSet<>(backgroundAgentIds);
            var known = ids.remove(agentId);
            backgroundAgentIds = ids;
            return known;
        }

        /** Background agents keep running when the turn that launched them ends. */
        void endForeground(SubagentState finalState, Instant at) {
            var ended = new LinkedHashMap<String, SubagentSnapshot>();
            subagents.forEach((id, agent) -> ended.put(id, agent.running() && !agent.background()
                    ? agent.finished(finalState, agent.lastMessage(), agent.transcriptPath(), at)
                    : agent));
            subagents = ended;
        }
    }

    static SessionState initial(String ccmSessionId, long projectId, Instant now) {
        return new SessionState(ccmSessionId, projectId, "", "", "", "", "", "", now, now, Map.of(), 0, "", false, false, false, Set.of());
    }

    SessionState withTopic(String newTopic) {
        return new SessionState(ccmSessionId, projectId, claudeSessionId, activity, permissionMode, model, title, newTopic, startedAt,
                lastEventAt, subagents, backgroundTasks, error, turnOpen, compacting, needsAction, backgroundAgentIds);
    }

    SessionState edit(Consumer<Draft> change) {
        var draft = new Draft(this);
        change.accept(draft);
        return new SessionState(ccmSessionId, projectId, draft.claudeSessionId, draft.activity, draft.permissionMode, draft.model,
                draft.title, topic, startedAt, draft.lastEventAt, bounded(draft.subagents), draft.backgroundTasks, draft.error,
                draft.turnOpen, draft.compacting, draft.needsAction, boundedIds(draft.backgroundAgentIds));
    }

    /** Status with no staleness cutoff. */
    AgentStatus status() {
        return status(Instant.EPOCH, Duration.ZERO);
    }

    /** Agents silent for [staleAfter] (zero or less: never) neither keep the session working nor ask for the user. */
    AgentStatus status(Instant now, Duration staleAfter) {
        var agents = subagents.values().stream().map(agent -> agent.withStale(now, staleAfter)).filter(agent -> agent.running() && !agent.stale()).toList();
        if (needsAction || agents.stream().anyMatch(agent -> agent.state() == SubagentState.NEEDS_ACTION)) {
            return AgentStatus.NEEDS_ACTION;
        }
        return turnOpen || compacting || !agents.isEmpty() ? AgentStatus.WORKING : AgentStatus.IDLE;
    }

    SessionSnapshot toSnapshot() {
        return toSnapshot(Instant.EPOCH, Duration.ZERO);
    }

    SessionSnapshot toSnapshot(Instant now, Duration staleAfter) {
        var agents = subagents.values().stream().map(agent -> agent.withStale(now, staleAfter)).toList();
        return new SessionSnapshot(ccmSessionId, projectId, nullIfEmpty(claudeSessionId), status(now, staleAfter), nullIfEmpty(activity),
                nullIfEmpty(permissionMode), nullIfEmpty(model), nullIfEmpty(title.isEmpty() ? topic : title), startedAt, lastEventAt,
                agents, backgroundTasks, nullIfEmpty(error));
    }

    private static Set<String> boundedIds(Set<String> ids) {
        return ids.size() <= MAX_SUBAGENTS ? Set.copyOf(ids) : Set.of();
    }

    /** Over the cap the oldest finished agents go first, then the oldest running ones. */
    private static Map<String, SubagentSnapshot> bounded(Map<String, SubagentSnapshot> value) {
        var copy = new LinkedHashMap<>(value);
        evictOldest(copy, false);
        evictOldest(copy, true);
        return Collections.unmodifiableMap(copy);
    }

    private static void evictOldest(Map<String, SubagentSnapshot> agents, boolean running) {
        var iterator = agents.values().iterator();
        while (agents.size() > MAX_SUBAGENTS && iterator.hasNext()) {
            if (iterator.next().running() == running) {
                iterator.remove();
            }
        }
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
