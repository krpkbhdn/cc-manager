package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable state of one live session; the status is derived from the flags, never stored.
 * Empty strings mean "unknown". blockingToolUseId is set only while a question or plan-approval tool waits.
 */
record SessionState(
        String ccmSessionId,
        long projectId,
        String claudeSessionId,
        String activity,
        String permissionMode,
        String model,
        String title,
        Instant startedAt,
        Instant lastEventAt,
        Map<String, SubagentSnapshot> subagents,
        int backgroundTasks,
        String error,
        boolean turnOpen,
        boolean compacting,
        boolean needsAction,
        String blockingToolUseId) {

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
        String blockingToolUseId;

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
            blockingToolUseId = s.blockingToolUseId;
        }

        /** Keeps the current value when the new one is empty. */
        void rememberMetadata(String newPermissionMode, String newModel, String newTitle) {
            permissionMode = newPermissionMode.isEmpty() ? permissionMode : newPermissionMode;
            model = newModel.isEmpty() ? model : newModel;
            title = newTitle.isEmpty() ? title : newTitle;
        }

        /** Waiting for the user; a tool use id marks a question or plan approval that its own PostToolUse resolves. */
        void requireAction(String toolUseId) {
            needsAction = true;
            blockingToolUseId = toolUseId.isEmpty() ? blockingToolUseId : toolUseId;
        }

        void resolveAction() {
            needsAction = false;
            blockingToolUseId = "";
        }

        void stopAllSubagents() {
            var stopped = new LinkedHashMap<String, SubagentSnapshot>();
            subagents.forEach((id, agent) -> stopped.put(id, agent.stopped()));
            subagents = stopped;
        }
    }

    static SessionState initial(String ccmSessionId, long projectId, Instant now) {
        return new SessionState(ccmSessionId, projectId, "", "", "", "", "", now, now, Map.of(), 0, "", false, false, false, "");
    }

    SessionState edit(Consumer<Draft> change) {
        var draft = new Draft(this);
        change.accept(draft);
        return new SessionState(ccmSessionId, projectId, draft.claudeSessionId, draft.activity, draft.permissionMode, draft.model,
                draft.title, startedAt, draft.lastEventAt, bounded(draft.subagents), draft.backgroundTasks, draft.error,
                draft.turnOpen, draft.compacting, draft.needsAction, draft.blockingToolUseId);
    }

    AgentStatus status() {
        if (needsAction) {
            return AgentStatus.NEEDS_ACTION;
        }
        return turnOpen || compacting || runningSubagents() ? AgentStatus.WORKING : AgentStatus.IDLE;
    }

    SessionSnapshot toSnapshot() {
        return new SessionSnapshot(ccmSessionId, projectId, nullIfEmpty(claudeSessionId), status(), nullIfEmpty(activity),
                nullIfEmpty(permissionMode), nullIfEmpty(model), nullIfEmpty(title), startedAt, lastEventAt,
                List.copyOf(subagents.values()), backgroundTasks, nullIfEmpty(error));
    }

    private boolean runningSubagents() {
        return subagents.values().stream().anyMatch(SubagentSnapshot::running);
    }

    private static Map<String, SubagentSnapshot> bounded(Map<String, SubagentSnapshot> value) {
        var copy = new LinkedHashMap<>(value);
        var iterator = copy.values().iterator();
        while (copy.size() > MAX_SUBAGENTS && iterator.hasNext()) {
            if (!iterator.next().running()) {
                iterator.remove();
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
