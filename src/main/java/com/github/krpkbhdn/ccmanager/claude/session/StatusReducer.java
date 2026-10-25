package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.claude.hook.ActivityFormatter;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure state machine of docs/claude-code-protocol.md section 8: previous state and hook event in, next state out. */
final class StatusReducer {

    private static final Set<String> USER_DECISION_TOOLS = Set.of("AskUserQuestion", "ExitPlanMode");
    private static final Set<String> AGENT_TOOLS = Set.of("Agent", "Task");
    private static final String BACKGROUND_SUBAGENT = "subagent";
    private static final String RUNNING = "running";
    private static final String API_ERROR = "API Error";
    private static final String ASYNC_LAUNCHED = "Async agent launched";
    private static final Pattern LAUNCHED_AGENT_ID = Pattern.compile("agent_?[iI]d\\\\?\"?\\s*[:=]\\s*\\\\?\"?([A-Za-z0-9_-]{1,64})");
    private static final Pattern SAFE_AGENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
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
        if (!(state.turnOpen() || state.compacting() || state.needsAction())) {
            return state;
        }
        return state.edit(draft -> {
            endTurn(draft);
            draft.endForeground(SubagentState.INTERRUPTED, Instant.now());
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
                rememberBackgroundLaunch(draft, event);
            });
            case PERMISSION_REQUEST, ELICITATION -> state.edit(draft -> draft.requireAction(""));
            case NOTIFICATION -> ActivityFormatter.needsAttention(event) ? state.edit(draft -> draft.requireAction("")) : state;
            case STOP, STOP_FAILURE -> state.edit(draft -> {
                endTurn(draft);
                listedRunningSubagents(event).stream().filter(id -> !id.isEmpty()).forEach(draft::markBackground);
                draft.endForeground(event.type() == HookEventType.STOP ? SubagentState.DONE : SubagentState.FAILED, event.receivedAt());
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
            draft.backgroundAgentIds = Set.of();
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

    /** A background launch returns at once, and its PostToolUse names the agent it started; a foreground call returns only after the agent ended. */
    private static void rememberBackgroundLaunch(SessionState.Draft draft, HookEvent event) {
        if (!AGENT_TOOLS.contains(event.text("tool_name"))) {
            return;
        }
        var response = event.payload().path("tool_response").toString();
        var asked = event.payload().path("tool_input").path("run_in_background").asBoolean(false);
        var matcher = LAUNCHED_AGENT_ID.matcher(response);
        if ((asked || response.contains(ASYNC_LAUNCHED)) && matcher.find()) {
            draft.markBackground(matcher.group(1));
        }
    }

    private static void endTurn(SessionState.Draft draft) {
        draft.turnOpen = false;
        draft.compacting = false;
        draft.resolveAction();
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
        return state.edit(draft -> {
            var background = draft.takeBackground(event.agentId());
            var agent = SubagentSnapshot.started(event.agentId(), agentType, background, liveTranscript(event), event.receivedAt());
            draft.subagents = with(draft.subagents, agent);
        });
    }

    private static SessionState subagentStopped(SessionState state, HookEvent event) {
        var agent = state.subagents().get(event.agentId());
        if (agent == null) {
            return state;
        }
        var message = event.text("last_assistant_message");
        var failed = agent.lastToolFailed() || message.startsWith(API_ERROR);
        var transcript = event.text("agent_transcript_path");
        var finished = agent.finished(failed ? SubagentState.FAILED : SubagentState.DONE, message,
                transcript.isEmpty() ? agent.transcriptPath() : transcript, event.receivedAt());
        return state.edit(draft -> {
            draft.subagents = with(draft.subagents, finished);
            draft.turnOpen = draft.turnOpen || agent.running() && agent.background();
        });
    }

    /** Stop lists the background tasks still running; a subagent among them outlives the lead's turn, whatever its launch call said. */
    private static List<String> listedRunningSubagents(HookEvent event) {
        var ids = new ArrayList<String>();
        event.payload().path("background_tasks").forEach(task -> {
            if (BACKGROUND_SUBAGENT.equals(task.path("type").asString("")) && RUNNING.equals(task.path("status").asString(""))) {
                ids.add(task.path("id").asString(""));
            }
        });
        return ids;
    }

    /** Claude writes a subagent's transcript next to the parent's while it runs, so the log can be read live. */
    private static String liveTranscript(HookEvent event) {
        var parent = event.text("transcript_path");
        if (parent.isEmpty() || !SAFE_AGENT_ID.matcher(event.agentId()).matches()) {
            return "";
        }
        return Path.of(parent.replaceFirst("\\.jsonl$", ""), "subagents", "agent-" + event.agentId() + ".jsonl").toString();
    }

    /** Only live agents progress, and the session's red comes from the agents' own state: a late event revives nothing. */
    private static SessionState subagentEvent(SessionState state, HookEvent event) {
        var agent = state.subagents().get(event.agentId());
        if (agent == null || !agent.running()) {
            return state;
        }
        var progressed = subagentProgress(agent, event);
        return state.edit(draft -> draft.subagents = with(draft.subagents, progressed));
    }

    private static SubagentSnapshot subagentProgress(SubagentSnapshot agent, HookEvent event) {
        var at = event.receivedAt();
        var waiting = ActivityFormatter.describe(event).orElse("");
        return switch (event.type()) {
            case PRE_TOOL_USE -> agent.toolStarted(ActivityFormatter.running(event), at);
            case POST_TOOL_USE, ELICITATION_RESULT -> agent.toolFinished(false, at);
            case POST_TOOL_USE_FAILURE -> agent.toolFinished(true, at);
            case PERMISSION_REQUEST, ELICITATION -> agent.waiting(waiting, at);
            case NOTIFICATION -> ActivityFormatter.needsAttention(event) ? agent.waiting(waiting, at) : agent;
            default -> agent;
        };
    }

    private static Map<String, SubagentSnapshot> with(Map<String, SubagentSnapshot> subagents, SubagentSnapshot agent) {
        var copy = new LinkedHashMap<>(subagents);
        copy.put(agent.agentId(), agent);
        return copy;
    }
}
