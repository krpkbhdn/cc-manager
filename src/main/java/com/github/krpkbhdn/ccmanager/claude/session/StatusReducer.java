package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.claude.hook.ActivityFormatter;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure state machine of docs/claude-code-protocol.md section 8: previous state and hook event in, next state out. */
final class StatusReducer {

    private static final Set<String> USER_DECISION_TOOLS = Set.of("AskUserQuestion", "ExitPlanMode");
    private static final String COMPACT_SOURCE = "compact";
    private static final String MANUAL_TRIGGER = "manual";
    private static final String UNKNOWN_ERROR = "unknown";

    private StatusReducer() {}

    static SessionState reduce(SessionState state, HookEvent event) {
        var observed = state.edit(draft -> observe(draft, event));
        var type = event.type();
        if (type == HookEventType.SUBAGENT_START) {
            return withActivity(subagentStarted(observed, event), event);
        }
        if (type == HookEventType.SUBAGENT_STOP) {
            return withActivity(subagentStopped(observed, event), event);
        }
        return event.fromSubagent() ? subagentEvent(observed, event) : withActivity(mainEvent(observed, event), event);
    }

    /** Esc fires no Stop, so the owner of the terminal reports the interrupt. */
    static SessionState interrupted(SessionState state) {
        if (state.status() == AgentStatus.IDLE) {
            return state;
        }
        return state.edit(draft -> {
            endTurn(draft);
            draft.activity = ActivityFormatter.INTERRUPTED;
        });
    }

    private static void observe(SessionState.Draft draft, HookEvent event) {
        draft.lastEventAt = event.receivedAt();
        if (!event.fromSubagent()) {
            draft.rememberMetadata(event.text("permission_mode"), event.text("model"), event.text("session_title"));
            var claudeSessionId = event.text("session_id");
            draft.claudeSessionId = claudeSessionId.isEmpty() ? draft.claudeSessionId : claudeSessionId;
        }
    }

    private static SessionState withActivity(SessionState state, HookEvent event) {
        return ActivityFormatter.describe(event).map(text -> state.edit(draft -> draft.activity = text)).orElse(state);
    }

    private static SessionState mainEvent(SessionState state, HookEvent event) {
        return switch (event.type()) {
            case SESSION_START -> sessionStarted(state, event);
            case USER_PROMPT_SUBMIT -> state.edit(draft -> {
                draft.turnOpen = true;
                draft.error = "";
                draft.resolveAction();
            });
            case PRE_TOOL_USE -> state.edit(draft -> preTool(draft, event));
            case POST_TOOL_USE, POST_TOOL_USE_FAILURE, ELICITATION_RESULT -> state.edit(draft -> {
                draft.turnOpen = true;
                draft.resolveAction();
            });
            case PERMISSION_REQUEST, ELICITATION -> state.edit(draft -> draft.requireAction(""));
            case NOTIFICATION -> ActivityFormatter.needsAttention(event) ? state.edit(draft -> draft.requireAction("")) : state;
            case STOP, STOP_FAILURE -> state.edit(draft -> {
                endTurn(draft);
                draft.backgroundTasks = event.count("background_tasks") + event.count("session_crons");
                draft.error = event.type() == HookEventType.STOP ? "" : failureOf(event);
            });
            case PRE_COMPACT -> state.edit(draft -> {
                draft.compacting = true;
                draft.turnOpen = true;
            });
            case POST_COMPACT -> state.edit(draft -> {
                draft.compacting = false;
                draft.turnOpen = draft.turnOpen && !MANUAL_TRIGGER.equals(event.text("trigger"));
            });
            default -> state;
        };
    }

    private static SessionState sessionStarted(SessionState state, HookEvent event) {
        if (COMPACT_SOURCE.equals(event.text("source"))) {
            return state;
        }
        return state.edit(draft -> {
            endTurn(draft);
            draft.subagents = Map.of();
            draft.backgroundTasks = 0;
            draft.error = "";
        });
    }

    private static void preTool(SessionState.Draft draft, HookEvent event) {
        draft.turnOpen = true;
        draft.resolveAction();
        if (USER_DECISION_TOOLS.contains(event.text("tool_name"))) {
            draft.requireAction(event.text("tool_use_id"));
        }
    }

    private static void endTurn(SessionState.Draft draft) {
        draft.turnOpen = false;
        draft.compacting = false;
        draft.resolveAction();
        draft.stopAllSubagents();
    }

    private static String failureOf(HookEvent event) {
        var error = event.text("error");
        return error.isEmpty() ? UNKNOWN_ERROR : error;
    }

    private static SessionState subagentStarted(SessionState state, HookEvent event) {
        var agentType = event.text("agent_type");
        if (agentType.isEmpty()) {
            return state;
        }
        var agent = new SubagentSnapshot(event.agentId(), agentType, "", true, event.receivedAt());
        return state.edit(draft -> draft.subagents = with(draft.subagents, agent));
    }

    private static SessionState subagentStopped(SessionState state, HookEvent event) {
        var agent = state.subagents().get(event.agentId());
        return agent == null ? state : state.edit(draft -> draft.subagents = with(draft.subagents, agent.stopped()));
    }

    private static SessionState subagentEvent(SessionState state, HookEvent event) {
        var agent = state.subagents().get(event.agentId());
        if (agent == null) {
            return state;
        }
        var described = ActivityFormatter.describe(event).map(agent::withActivity).orElse(agent);
        return state.edit(draft -> {
            draft.subagents = with(draft.subagents, described);
            subagentWait(draft, event);
        });
    }

    private static void subagentWait(SessionState.Draft draft, HookEvent event) {
        switch (event.type()) {
            case PERMISSION_REQUEST, ELICITATION -> draft.requireAction("");
            case NOTIFICATION -> {
                if (ActivityFormatter.needsAttention(event)) {
                    draft.requireAction("");
                }
            }
            case POST_TOOL_USE, POST_TOOL_USE_FAILURE, ELICITATION_RESULT -> {
                if (draft.needsAction && draft.blockingToolUseId.isEmpty()) {
                    draft.resolveAction();
                }
            }
            default -> { }
        }
    }

    private static Map<String, SubagentSnapshot> with(Map<String, SubagentSnapshot> subagents, SubagentSnapshot agent) {
        var copy = new LinkedHashMap<>(subagents);
        copy.put(agent.agentId(), agent);
        return copy;
    }
}
