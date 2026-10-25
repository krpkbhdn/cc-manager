package com.github.krpkbhdn.ccmanager.claude.session;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.CCM_ID;
import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
    private static final String ASYNC = "{\"tool_name\":\"Agent\",\"tool_input\":{\"run_in_background\":true},"
            + "\"tool_response\":\"Async agent launched successfully.\\nagentId: %s (internal ID)\"}";
    private static final String FOREGROUND = "{\"tool_name\":\"Agent\",\"tool_input\":{},\"tool_response\":\"Done.\\nagentId: %s\"}";

    private void start(String id) {
        apply("SubagentStart", "{\"agent_id\":\"" + id + "\",\"agent_type\":\"developer\"}");
    }

    private SubagentSnapshot agent(String id) {
        return state.toSnapshot().subagents().stream().filter(a -> a.agentId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void backgroundSubagentSurvivesParentStopAndEndsWithItsOwnSubagentStop() {
        apply("UserPromptSubmit", "{}");
        start("a1");
        apply("PostToolUse", ASYNC.formatted("a1"));

        var parentDone = apply("Stop", "{}");

        assertThat(parentDone.status()).isEqualTo(AgentStatus.WORKING);
        assertThat(agent("a1").background()).isTrue();

        var finished = apply("SubagentStop", "{\"agent_id\":\"a1\",\"agent_type\":\"developer\",\"last_assistant_message\":\"Done it\","
                + "\"agent_transcript_path\":\"/t/a1.jsonl\"}");

        assertThat(finished.status()).isEqualTo(AgentStatus.WORKING);
        assertThat(apply("Stop", "{}").status()).isEqualTo(AgentStatus.IDLE);
        assertThat(agent("a1").state()).isEqualTo(SubagentState.DONE);
        assertThat(agent("a1").lastMessage()).isEqualTo("Done it");
        assertThat(agent("a1").transcriptPath()).isEqualTo("/t/a1.jsonl");
        assertThat(agent("a1").endedAt()).isNotNull();
    }

    @Test
    void parallelLaunchesAreCorrelatedByAgentIdInAnyEventOrder() {
        apply("UserPromptSubmit", "{}");
        start("fg");
        apply("PostToolUse", ASYNC.formatted("b2"));
        start("b1");
        start("b2");
        apply("PostToolUse", ASYNC.formatted("b1"));
        apply("PostToolUse", FOREGROUND.formatted("fg"));

        apply("Stop", "{}");

        assertThat(agent("fg").background()).isFalse();
        assertThat(agent("fg").state()).isEqualTo(SubagentState.DONE);
        assertThat(agent("b1").background()).isTrue();
        assertThat(agent("b2").background()).isTrue();
        assertThat(state.toSnapshot().status()).isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void deniedAgentCallLeavesNoBackgroundMarkBehind() {
        apply("UserPromptSubmit", "{}");
        apply("PostToolUseFailure", "{\"tool_name\":\"Agent\",\"tool_input\":{\"run_in_background\":true}}");

        start("fg");
        apply("Stop", "{}");

        assertThat(agent("fg").background()).isFalse();
        assertThat(agent("fg").state()).isEqualTo(SubagentState.DONE);
    }

    @Test
    void sessionStartForgetsPendingBackgroundMarksAndAgents() {
        apply("UserPromptSubmit", "{}");
        apply("PostToolUse", ASYNC.formatted("b1"));
        apply("SessionStart", "{\"source\":\"clear\"}");

        start("b1");

        assertThat(agent("b1").background()).isFalse();
    }

    @Test
    void foregroundSubagentLeftRunningIsClosedByInterruptButBackgroundOneIsKept() {
        apply("UserPromptSubmit", "{}");
        start("fg");
        start("bg");
        apply("PostToolUse", ASYNC.formatted("bg"));

        state = StatusReducer.interrupted(state);

        assertThat(state.toSnapshot().subagents()).extracting(SubagentSnapshot::agentId, SubagentSnapshot::state)
                .containsExactly(tuple("fg", SubagentState.INTERRUPTED), tuple("bg", SubagentState.WORKING));
    }

    @Test
    void lateEventOfFinishedAgentChangesNeitherItNorTheSession() {
        apply("UserPromptSubmit", "{}");
        start("a1");
        apply("SubagentStop", "{\"agent_id\":\"a1\",\"agent_type\":\"developer\"}");
        apply("Stop", "{}");

        var late = apply("PermissionRequest", "{\"agent_id\":\"a1\",\"tool_name\":\"Bash\"}");

        assertThat(late.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(agent("a1").state()).isEqualTo(SubagentState.DONE);
    }

    @Test
    void sessionStaysRedWhileAnyAgentWaitsAndOtherEventsDoNotClearIt() {
        apply("UserPromptSubmit", "{}");
        start("a");
        start("b");
        apply("PermissionRequest", "{\"agent_id\":\"a\",\"tool_name\":\"Bash\"}");

        apply("PostToolUse", "{\"agent_id\":\"b\",\"tool_name\":\"Read\"}");
        apply("PreToolUse", "{\"tool_name\":\"Read\",\"tool_use_id\":\"t\"}");
        var main = apply("PostToolUse", "{\"tool_name\":\"Read\",\"tool_use_id\":\"t\"}");

        assertThat(main.status()).isEqualTo(AgentStatus.NEEDS_ACTION);
        assertThat(apply("PostToolUse", "{\"agent_id\":\"a\",\"tool_name\":\"Bash\"}").status()).isEqualTo(AgentStatus.WORKING);
    }

    @Test
    void silentRunningAgentIsStaleAndNoLongerKeepsTheSessionWorking() {
        apply("UserPromptSubmit", "{}");
        start("bg");
        apply("PostToolUse", ASYNC.formatted("bg"));
        apply("Stop", "{}");

        var later = state.toSnapshot(Instant.now(), java.time.Duration.ofMinutes(10));

        assertThat(later.subagents().get(0).stale()).isTrue();
        assertThat(later.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(later.subagents().get(0).state()).isEqualTo(SubagentState.WORKING);
    }

    @Test
    void agentsAreBoundedEvictingFinishedBeforeRunning() {
        apply("UserPromptSubmit", "{}");
        start("keep");
        for (var i = 0; i < 60; i++) {
            start("f" + i);
            apply("SubagentStop", "{\"agent_id\":\"f" + i + "\",\"agent_type\":\"developer\"}");
        }

        assertThat(state.toSnapshot().subagents()).hasSize(50);
        assertThat(agent("keep").running()).isTrue();
    }

    @Test
    void unsafeAgentIdGetsNoTranscriptPath() {
        apply("UserPromptSubmit", "{}");

        apply("SubagentStart", "{\"agent_id\":\"../../x\",\"agent_type\":\"Explore\",\"transcript_path\":\"/p/s1.jsonl\"}");

        assertThat(state.toSnapshot().subagents().get(0).transcriptPath()).isEmpty();
    }

    @Test
    void subagentShowsCurrentToolThinkingBetweenToolsAndCountsCalls() {
        apply("UserPromptSubmit", "{}");
        apply("SubagentStart", "{\"agent_id\":\"a1\",\"agent_type\":\"developer\"}");

        var editing = apply("PreToolUse", "{\"agent_id\":\"a1\",\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"C:/x/AppState.kt\"}}");
        assertThat(editing.subagents().get(0).activity()).isEqualTo("Edit: AppState.kt");

        var thinking = apply("PostToolUse", "{\"agent_id\":\"a1\",\"tool_name\":\"Edit\"}");
        assertThat(thinking.subagents().get(0).activity()).isEmpty();
        assertThat(thinking.subagents().get(0).toolCalls()).isEqualTo(1);
    }

    @Test
    void subagentNeedingPermissionIsMarkedAndResumesAfterItsToolCompletes() {
        apply("UserPromptSubmit", "{}");
        apply("SubagentStart", "{\"agent_id\":\"a1\",\"agent_type\":\"tester\"}");

        var waiting = apply("PermissionRequest", "{\"agent_id\":\"a1\",\"tool_name\":\"Bash\"}");
        assertThat(waiting.subagents().get(0).state()).isEqualTo(SubagentState.NEEDS_ACTION);

        var resumed = apply("PostToolUse", "{\"agent_id\":\"a1\",\"tool_name\":\"Bash\"}");
        assertThat(resumed.subagents().get(0).state()).isEqualTo(SubagentState.WORKING);
    }

    @Test
    void subagentWhoseLastToolFailedEndsFailedAndLateEventsDoNotReviveIt() {
        apply("UserPromptSubmit", "{}");
        apply("SubagentStart", "{\"agent_id\":\"a1\",\"agent_type\":\"tester\"}");
        apply("PostToolUseFailure", "{\"agent_id\":\"a1\",\"tool_name\":\"Bash\"}");
        apply("SubagentStop", "{\"agent_id\":\"a1\",\"agent_type\":\"tester\",\"last_assistant_message\":\"gave up\"}");

        var late = apply("PreToolUse", "{\"agent_id\":\"a1\",\"tool_name\":\"Read\"}");

        assertThat(late.subagents().get(0).state()).isEqualTo(SubagentState.FAILED);
    }

    @Test
    void runningSubagentGetsItsTranscriptPathFromTheParentTranscript() {
        apply("UserPromptSubmit", "{}");

        var started = apply("SubagentStart", "{\"agent_id\":\"a9\",\"agent_type\":\"Explore\",\"transcript_path\":\"/p/s1.jsonl\"}");

        assertThat(started.subagents().get(0).transcriptPath()).isEqualTo(java.nio.file.Path.of("/p/s1", "subagents", "agent-a9.jsonl").toString());
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
    private static final String BACKGROUND_STOP = "{\"background_tasks\":[{\"id\":\"a1\",\"type\":\"subagent\",\"status\":\"running\"}],\"session_crons\":[]}";
    private static final String EMPTY_STOP = "{\"background_tasks\":[],\"session_crons\":[]}";

    private SessionSnapshot startSubagentThenStop(String stopJson) {
        apply("UserPromptSubmit", "{}");
        start("a1");
        return apply("Stop", stopJson);
    }

    @Test
    void stopKeepsSubagentListedAsRunningBackgroundTaskEvenWithoutLaunchResponse() {
        var snapshot = startSubagentThenStop(BACKGROUND_STOP);

        assertThat(snapshot.status()).isEqualTo(AgentStatus.WORKING);
        assertThat(agent("a1").background()).isTrue();
        assertThat(agent("a1").running()).isTrue();
    }

    @Test
    void stopWithoutBackgroundTasksClosesSubagentAndGoesIdle() {
        var snapshot = startSubagentThenStop(EMPTY_STOP);

        assertThat(snapshot.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(agent("a1").running()).isFalse();
    }

    @Test
    void backgroundSubagentFinishingReopensLeadTurnUntilNextStop() {
        startSubagentThenStop(BACKGROUND_STOP);

        assertThat(apply("SubagentStop", "{\"agent_id\":\"a1\",\"agent_type\":\"developer\"}").status()).isEqualTo(AgentStatus.WORKING);
        assertThat(apply("Stop", EMPTY_STOP).status()).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void foregroundSubagentFinishingDoesNotReopenAClosedTurn() {
        apply("UserPromptSubmit", "{}");
        start("fg");
        apply("Stop", EMPTY_STOP);

        assertThat(apply("SubagentStop", "{\"agent_id\":\"fg\",\"agent_type\":\"developer\"}").status()).isEqualTo(AgentStatus.IDLE);
    }
}
