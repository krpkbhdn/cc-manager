package com.github.krpkbhdn.ccmanager.claude.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.hook.HookProperties;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.Denial;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.FormAnswer;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow;
import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProposalServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int WAIT_SECONDS = 2;
    private static final int MAX_HELD = 3;
    private static final int ROOMY_CAP = 1000;
    private static final String BASH_LS = "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}";
    private static final Duration SHORT_HOLD = Duration.ofMillis(100);

    private final SessionRegistry registry = new SessionRegistry(new SessionProperties(Duration.ofMinutes(10), Duration.ofMinutes(10)), mock(TranscriptService.class));
    private final ProposalService service = serviceHolding(Duration.ofSeconds(30));
    private final String session = registry.register(1L);

    private ProposalService serviceHolding(Duration hold) {
        return serviceHolding(hold, ROOMY_CAP);
    }

    private ProposalService serviceHolding(Duration hold, int cap) {
        return new ProposalService(registry, new HookProperties(10, 1024, 500, hold, cap, 1000));
    }

    @Test
    void holdBeyondTheCapIsReleasedAtOnceLeavingTheTerminalInCharge() throws Exception {
        var capped = serviceHolding(Duration.ofSeconds(30), MAX_HELD);
        var inside = new ArrayList<CompletableFuture<Optional<String>>>();
        for (int i = 0; i < MAX_HELD; i++) {
            inside.add(holdBash(capped));
        }

        assertThat(released(holdBash(capped))).isTrue();
        assertThat(capped.pending()).hasSize(MAX_HELD);
        assertThat(inside).noneMatch(CompletableFuture::isDone);
    }

    private CompletableFuture<AppDecision> holdForApp(ProposalService target) {
        registry.on(event(session, HookEventType.PRE_TOOL_USE, BASH_LS));
        var proposal = new PermissionProposal("app-1", session, Instant.now(), "", "mcp__cc-manager-app__git_push", MAPPER.readTree("{}"), List.of());
        return target.holdForApp(proposal);
    }

    @Test
    void appHeldProposalCompletesWithTheUsersAnswerAndNoHookOutput() throws Exception {
        var held = holdForApp(service);
        assertThat(held).isNotDone();
        assertThat(service.pending()).hasSize(1);

        assertThat(service.answer("app-1", PermissionAllow.once())).isTrue();

        assertThat(held.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOfSatisfying(AppDecision.Answered.class,
                answered -> assertThat(answered.answer()).isInstanceOf(PermissionAllow.class));
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void appHeldProposalNamesWhyThereIsNoAnswerOnDismissTimeoutAndWhenTheSessionGoesIdle() throws Exception {
        var dismissed = holdForApp(service);
        assertThat(service.dismiss("app-1")).isTrue();
        assertThat(dismissed.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(AppDecision.Unanswered.RELEASED);

        assertThat(holdForApp(serviceHolding(SHORT_HOLD)).get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(AppDecision.Unanswered.TIMED_OUT);

        var interrupted = holdForApp(service);
        registry.markInterrupted(session);
        assertThat(interrupted.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(AppDecision.Unanswered.RELEASED);
    }

    @Test
    void appHeldProposalBeyondTheCapIsRefusedAtOnce() throws Exception {
        var capped = serviceHolding(Duration.ofSeconds(30), 0);

        assertThat(holdForApp(capped).get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(AppDecision.Unanswered.TOO_MANY_PENDING);
    }

    private HookEvent event(String sessionId, HookEventType type, String json) {
        var payload = MAPPER.readTree(json);
        return new HookEvent(sessionId, type, type.wireName(), payload, Instant.now());
    }

    private CompletableFuture<Optional<String>> holdBash(ProposalService target) {
        var request = event(session, HookEventType.PERMISSION_REQUEST, BASH_LS);
        registry.on(request);
        return target.hold(request);
    }

    private CompletableFuture<Optional<String>> holdBash(String json) {
        var request = event(session, HookEventType.PERMISSION_REQUEST, json);
        registry.on(request);
        return service.hold(request);
    }

    private CompletableFuture<Optional<String>> holdBash() {
        return holdBash(BASH_LS);
    }

    private boolean released(CompletableFuture<Optional<String>> held) throws Exception {
        return held.get(WAIT_SECONDS, TimeUnit.SECONDS).isEmpty();
    }

    @Test
    void answerCompletesTheHeldCallWithTheRenderedOutputAndRemovesTheProposal() throws Exception {
        var held = holdBash();
        var proposal = service.pending().getFirst();

        assertThat(service.answer(proposal.id(), PermissionAllow.once())).isTrue();

        assertThat(held.get(WAIT_SECONDS, TimeUnit.SECONDS)).hasValueSatisfying(output -> assertThat(output).contains("\"behavior\":\"allow\""));
        assertThat(service.pending()).isEmpty();
        assertThat(service.answer(proposal.id(), PermissionAllow.once())).isFalse();
    }

    @Test
    void answerOfAnotherKindIsRejectedAndKeepsTheProposal() {
        holdBash();
        var proposal = service.pending().getFirst();

        assertThatThrownBy(() -> service.answer(proposal.id(), FormAnswer.cancel())).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.pending()).hasSize(1);
    }

    @Test
    void holdForASessionThatIsAlreadyGoneReleasesAtOnce() throws Exception {
        var request = event("gone", HookEventType.PERMISSION_REQUEST, "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}");

        assertThat(released(service.hold(request))).isTrue();
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void dismissReleasesTheHeldCallWithAnEmptyBody() throws Exception {
        var held = holdBash();

        assertThat(service.dismiss(service.pending().getFirst().id())).isTrue();

        assertThat(released(held)).isTrue();
        assertThat(service.pending()).isEmpty();
        assertThat(service.dismiss("unknown")).isFalse();
    }

    @Test
    void severalProposalsOfOneSessionAndOtherSessionsStaySeparate() {
        holdBash();
        holdBash("{\"tool_name\":\"Edit\",\"agent_id\":\"sub-1\",\"tool_input\":{}}");
        var otherSession = registry.register(2L);
        var otherRequest = event(otherSession, HookEventType.PERMISSION_REQUEST, "{\"tool_name\":\"Write\"}");
        registry.on(otherRequest);
        service.hold(otherRequest);

        assertThat(service.pending()).hasSize(3);
        assertThat(service.pending()).filteredOn(proposal -> proposal.ccmSessionId().equals(session)).hasSize(2);
        assertThat(service.pending()).filteredOn(proposal -> proposal.ccmSessionId().equals(otherSession)).hasSize(1);
    }

    @Test
    void releaseAllHandsEveryHeldCallBackToTheTerminal() throws Exception {
        var first = holdBash();
        var second = holdBash("{\"tool_name\":\"Edit\",\"agent_id\":\"sub-1\",\"tool_input\":{}}");

        service.releaseAll();

        assertThat(released(first)).isTrue();
        assertThat(released(second)).isTrue();
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void eventsThatDoNotYieldAProposalAreReleasedAtOnce() throws Exception {
        assertThat(released(service.hold(event(session, HookEventType.PERMISSION_REQUEST, "{}")))).isTrue();
        assertThat(released(service.hold(event(session, HookEventType.STOP, "{}")))).isTrue();
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void postToolUseOfTheSameToolAndAgentSettlesTheProposal() throws Exception {
        var main = holdBash();
        var subagent = holdBash("{\"tool_name\":\"Bash\",\"agent_id\":\"sub-1\",\"tool_input\":{}}");

        service.on(event(session, HookEventType.POST_TOOL_USE, "{\"tool_name\":\"Edit\",\"tool_input\":{\"command\":\"ls\"}}"));
        assertThat(service.pending()).hasSize(2);
        service.on(event("other-session", HookEventType.POST_TOOL_USE, "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}"));
        assertThat(service.pending()).hasSize(2);
        service.on(event(session, HookEventType.POST_TOOL_USE_FAILURE, "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}"));

        assertThat(released(main)).isTrue();
        assertThat(service.pending()).hasSize(1);
        service.on(event(session, HookEventType.PERMISSION_DENIED, "{\"tool_name\":\"Bash\",\"agent_id\":\"sub-1\",\"tool_input\":{}}"));
        assertThat(released(subagent)).isTrue();
    }

    @Test
    void postToolUseSettlesOnlyTheParallelProposalWithTheSameInput() throws Exception {
        var first = holdBash("{\"tool_name\":\"WebFetch\",\"tool_input\":{\"url\":\"https://a\"}}");
        var second = holdBash("{\"tool_name\":\"WebFetch\",\"tool_input\":{\"url\":\"https://b\"}}");

        service.on(event(session, HookEventType.POST_TOOL_USE, "{\"tool_name\":\"WebFetch\",\"tool_input\":{\"url\":\"https://a\"}}"));

        assertThat(released(first)).isTrue();
        assertThat(second).isNotDone();
        assertThat(service.pending()).hasSize(1);
    }

    @Test
    void turnEndAndNewPromptSettleEveryProposalOfTheSession() throws Exception {
        for (var type : List.of(HookEventType.STOP, HookEventType.STOP_FAILURE, HookEventType.USER_PROMPT_SUBMIT, HookEventType.SESSION_END)) {
            var held = holdBash();

            service.on(event(session, type, "{}"));

            assertThat(released(held)).as(type.name()).isTrue();
        }
    }

    @Test
    void elicitationResultSettlesFormsButNotToolProposals() throws Exception {
        var tool = holdBash();
        var form = service.hold(event(session, HookEventType.ELICITATION, "{\"mcp_server_name\":\"jira\",\"message\":\"m\"}"));

        service.on(event(session, HookEventType.ELICITATION_RESULT, "{}"));

        assertThat(released(form)).isTrue();
        assertThat(tool).isNotDone();
    }

    @Test
    void escInTheTerminalReleasesTheProposal() throws Exception {
        var held = holdBash();
        assertThat(held).isNotDone();

        registry.markInterrupted(session);

        assertThat(released(held)).isTrue();
    }

    @Test
    void endOfTheProcessReleasesTheProposal() throws Exception {
        var held = holdBash();

        registry.markEnded(session, 0);

        assertThat(released(held)).isTrue();
        assertThat(service.pending()).isEmpty();
    }

    @Test
    void unansweredProposalIsDroppedAfterTheHoldTime() throws Exception {
        var shortLived = serviceHolding(SHORT_HOLD);
        var held = shortLived.hold(event(session, HookEventType.PERMISSION_REQUEST, "{\"tool_name\":\"Bash\"}"));

        assertThat(released(held)).isTrue();
        assertThat(shortLived.pending()).isEmpty();
    }

    @Test
    void subscribersHearAboutNewAndGoneProposalsUntilTheyUnsubscribe() {
        var changes = new AtomicInteger();
        var unsubscribe = service.subscribe(changes::incrementAndGet);

        holdBash();
        service.dismiss(service.pending().getFirst().id());
        assertThat(changes).hasValue(2);

        unsubscribe.run();
        holdBash();
        assertThat(changes).hasValue(2);
    }

    @Test
    void concurrentHoldsAndAnswersLoseNothing() throws Exception {
        var count = 200;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<CompletableFuture<Optional<String>>>();
            for (var i = 0; i < count; i++) {
                futures.add(CompletableFuture.supplyAsync(this::holdBash, pool).thenCompose(held -> held));
            }
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (service.pending().size() < count) {
                assertThat(System.nanoTime()).isLessThan(deadline);
                Thread.sleep(5);
            }
            service.pending().forEach(proposal -> pool.execute(() -> service.answer(proposal.id(), new Denial("no"))));
            for (var future : futures) {
                assertThat(future.get(WAIT_SECONDS, TimeUnit.SECONDS)).isPresent();
            }
        }
        assertThat(service.pending()).isEmpty();
    }
}
