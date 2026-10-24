package com.github.krpkbhdn.ccmanager.claude.session;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.CCM_ID;
import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StatusReducerTest {

    private SessionState state = SessionState.initial(CCM_ID, 7, Instant.EPOCH);

    private SessionSnapshot apply(HookEvent... events) {
        for (var event : events) {
            state = StatusReducer.reduce(state, event);
        }
        return state.toSnapshot();
    }

    private SessionSnapshot apply(String eventName, String json) {
        return apply(of(eventName, json));
    }

    @Test
    void fullTurnGoesIdleWorkingIdle() {
        assertThat(apply("SessionStart", "{\"session_id\":\"c1\",\"source\":\"startup\",\"model\":\"haiku\"}").status())
                .isEqualTo(AgentStatus.IDLE);
        assertThat(apply("UserPromptSubmit", "{\"prompt\":\"fix the bug\",\"permission_mode\":\"plan\"}").status())
                .isEqualTo(AgentStatus.WORKING);
        var working = apply("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git status\"},\"tool_use_id\":\"t1\"}");
        assertThat(working.activity()).isEqualTo("Bash: git status");
        assertThat(apply("PostToolUse", "{\"tool_name\":\"Bash\",\"tool_use_id\":\"t1\"}").status()).isEqualTo(AgentStatus.WORKING);

        var done = apply("Stop", "{\"last_assistant_message\":\"All done\",\"background_tasks\":[],\"session_crons\":[]}");

        assertThat(done.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(done.activity()).isEqualTo("Finished: All done");
        assertThat(done.model()).isEqualTo("haiku");
        assertThat(done.permissionMode()).isEqualTo("plan");
        assertThat(done.claudeSessionId()).isEqualTo("c1");
    }

    @Test
    void permissionRequestIsInstantRedAndResolvedByPostToolUse() {
        apply("UserPromptSubmit", "{}");
        apply("PreToolUse", "{\"tool_name\":\"Bash\",\"tool_use_id\":\"t1\"}");

        var waiting = apply("PermissionRequest", "{\"tool_name\":\"Bash\"}");

        assertThat(waiting.status()).isEqualTo(AgentStatus.NEEDS_ACTION);
        assertThat(waiting.activity()).isEqualTo("Waiting for permission: Bash");
        assertThat(apply("PostToolUse", "{\"tool_name\":\"Bash\",\"tool_use_id\":\"t1\"}").status()).isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void permissionDeniedTurnEndsWithStop() {
        apply("UserPromptSubmit", "{}");
        apply("PermissionRequest", "{\"tool_name\":\"Bash\"}");

        assertThat(apply("Stop", "{}").status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void attentionNotificationIsRedButIdleNudgeIsIgnored() {
        apply("UserPromptSubmit", "{}");

        assertThat(apply("Notification", "{\"notification_type\":\"permission_prompt\",\"message\":\"needs you\"}").status())
                .isEqualTo(AgentStatus.NEEDS_ACTION);
        apply("UserPromptSubmit", "{}");
        apply("Stop", "{}");
        assertThat(apply("Notification", "{\"notification_type\":\"idle_prompt\"}").status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void askUserQuestionWaitsUntilItsOwnPostToolUse() {
        apply("UserPromptSubmit", "{}");

        var asking = apply("PreToolUse", "{\"tool_name\":\"AskUserQuestion\",\"tool_use_id\":\"q1\"}");

        assertThat(asking.status()).isEqualTo(AgentStatus.NEEDS_ACTION);
        assertThat(apply("PostToolUse", "{\"tool_name\":\"AskUserQuestion\",\"tool_use_id\":\"q1\"}").status())
                .isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void exitPlanModeWaitsAndNextPreToolUseOfAnotherToolResumes() {
        apply("UserPromptSubmit", "{}");
        apply("PreToolUse", "{\"tool_name\":\"ExitPlanMode\",\"tool_use_id\":\"p1\"}");
        apply("PermissionRequest", "{\"tool_name\":\"ExitPlanMode\"}");

        assertThat(apply("PreToolUse", "{\"tool_name\":\"Read\",\"tool_use_id\":\"t2\"}").status()).isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void elicitationWaitsUntilResult() {
        apply("UserPromptSubmit", "{}");

        assertThat(apply("Elicitation", "{\"mcp_server_name\":\"db\"}").status()).isEqualTo(AgentStatus.NEEDS_ACTION);
        assertThat(apply("ElicitationResult", "{\"mcp_server_name\":\"db\"}").status()).isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void subagentLifecycleTracksChildWithoutChangingMainStatus() {
        apply("UserPromptSubmit", "{}");
        apply("SubagentStart", "{\"agent_id\":\"a1\",\"agent_type\":\"Explore\"}");

        var running = apply("PreToolUse", "{\"agent_id\":\"a1\",\"agent_type\":\"Explore\",\"tool_name\":\"Grep\",\"tool_input\":{\"pattern\":\"TODO\"}}");

        assertThat(running.status()).isEqualTo(AgentStatus.WORKING);
        assertThat(running.activity()).isEqualTo("Subagent Explore started");
        assertThat(running.subagents()).singleElement().satisfies(agent -> {
            assertThat(agent.running()).isTrue();
            assertThat(agent.activity()).isEqualTo("Grep: TODO");
        });

        var stopped = apply("SubagentStop", "{\"agent_id\":\"a1\",\"agent_type\":\"Explore\"}");

        assertThat(stopped.subagents()).singleElement().satisfies(agent -> assertThat(agent.running()).isFalse());
        assertThat(apply("Stop", "{}").status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void internalHelperSubagentWithEmptyTypeIsIgnored() {
        apply("SessionStart", "{\"source\":\"startup\"}");

        var snapshot = apply("SubagentStart", "{\"agent_id\":\"h1\",\"agent_type\":\"\"}");

        assertThat(snapshot.subagents()).isEmpty();
        assertThat(snapshot.status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void subagentToolEventDoesNotClearMainQuestion() {
        apply("UserPromptSubmit", "{}");
        apply("SubagentStart", "{\"agent_id\":\"a1\",\"agent_type\":\"Plan\"}");
        apply("PreToolUse", "{\"tool_name\":\"AskUserQuestion\",\"tool_use_id\":\"q1\"}");

        var snapshot = apply("PostToolUse", "{\"agent_id\":\"a1\",\"agent_type\":\"Plan\",\"tool_name\":\"Read\"}");

        assertThat(snapshot.status()).isEqualTo(AgentStatus.NEEDS_ACTION);
    }

    @Test
    void subagentPermissionPromptIsRedAndClearedBySubagentToolCompletion() {
        apply("UserPromptSubmit", "{}");
        apply("SubagentStart", "{\"agent_id\":\"a1\",\"agent_type\":\"Plan\"}");

        assertThat(apply("PermissionRequest", "{\"agent_id\":\"a1\",\"agent_type\":\"Plan\",\"tool_name\":\"Bash\"}").status())
                .isEqualTo(AgentStatus.NEEDS_ACTION);
        assertThat(apply("PostToolUse", "{\"agent_id\":\"a1\",\"agent_type\":\"Plan\",\"tool_name\":\"Bash\"}").status())
                .isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void clearKeepsSessionButRekeysClaudeSessionAndResetsSubagents() {
        apply("SessionStart", "{\"session_id\":\"old\",\"source\":\"startup\"}");
        apply("UserPromptSubmit", "{\"session_id\":\"old\"}");
        apply("SubagentStart", "{\"session_id\":\"old\",\"agent_id\":\"a1\",\"agent_type\":\"Explore\"}");
        apply("SessionEnd", "{\"session_id\":\"old\",\"reason\":\"clear\"}");

        var cleared = apply("SessionStart", "{\"session_id\":\"new\",\"source\":\"clear\"}");

        assertThat(cleared.ccmSessionId()).isEqualTo(CCM_ID);
        assertThat(cleared.claudeSessionId()).isEqualTo("new");
        assertThat(cleared.subagents()).isEmpty();
        assertThat(cleared.status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void compactionKeepsWorkingUntilPostCompactAndManualCompactEndsIdle() {
        apply("UserPromptSubmit", "{}");
        assertThat(apply("PreCompact", "{\"trigger\":\"auto\"}").activity()).isEqualTo("Compacting context");
        assertThat(apply("SessionStart", "{\"source\":\"compact\"}").status()).isEqualTo(AgentStatus.WORKING);
        assertThat(apply("PostCompact", "{\"trigger\":\"auto\"}").status()).isEqualTo(AgentStatus.WORKING);

        apply("Stop", "{}");
        apply("PreCompact", "{\"trigger\":\"manual\"}");
        assertThat(apply("PostCompact", "{\"trigger\":\"manual\"}").status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void interruptTurnsWorkingAndRedIntoIdle() {
        apply("UserPromptSubmit", "{}");
        state = StatusReducer.interrupted(state);
        assertThat(state.toSnapshot().status()).isEqualTo(AgentStatus.IDLE);

        apply("PermissionRequest", "{\"tool_name\":\"Bash\"}");
        state = StatusReducer.interrupted(state);
        assertThat(state.toSnapshot().status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void stopFailureEndsTurnWithErrorThatNextPromptClears() {
        apply("UserPromptSubmit", "{}");

        var failed = apply("StopFailure", "{\"error\":\"rate_limit\"}");

        assertThat(failed.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(failed.error()).isEqualTo("rate_limit");
        assertThat(failed.activity()).isEqualTo("Error: rate_limit");
        assertThat(apply("UserPromptSubmit", "{}").error()).isNull();
    }

    @Test
    void stopWithBackgroundTasksIsIdleAndCountsThem() {
        apply("UserPromptSubmit", "{}");

        var stopped = apply("Stop", "{\"background_tasks\":[{\"id\":\"b1\"},{\"id\":\"b2\"}],\"session_crons\":[{\"id\":\"c1\"}]}");

        assertThat(stopped.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(stopped.backgroundTasks()).isEqualTo(3);
    }

    @Test
    void sessionEndAloneDoesNotChangeStatus() {
        apply("UserPromptSubmit", "{}");

        assertThat(apply("SessionEnd", "{\"reason\":\"other\"}").status()).isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void everyEventUpdatesLastEventAt() {
        var event = of("InstructionsLoaded");

        assertThat(apply(event).lastEventAt()).isEqualTo(event.receivedAt());
    }
}
