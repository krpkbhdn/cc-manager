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
    private static final Duration SHORT_HOLD = Duration.ofMillis(100);

    private final SessionRegistry registry = new SessionRegistry(new SessionProperties(Duration.ofMinutes(10)), mock(TranscriptService.class));
    private final ProposalService service = serviceHolding(Duration.ofSeconds(30));
    private final String session = registry.register(1L);

    private ProposalService serviceHolding(Duration hold) {
        return new ProposalService(registry, new HookProperties(10, 1024, 500, hold));
    }

    private HookEvent event(String sessionId, HookEventType type, String json) {
        var payload = MAPPER.readTree(json);
        return new HookEvent(sessionId, type, type.wireName(), payload, Instant.now());
    }

    private CompletableFuture<Optional<String>> holdBash(String json) {
        var request = event(session, HookEventType.PERMISSION_REQUEST, json);
        registry.on(request);
        return service.hold(request);
    }

    private CompletableFuture<Optional<String>> holdBash() {
        return holdBash("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}");
    }

    private boolean released(CompletableFuture<Optional<String>> held) throws Exception {
        return held.get(WAIT_SECONDS, TimeUnit.SECONDS).isEmpty();
    }

    @Test
    void answerCompletesTheHeldCallWithTheRenderedOutputAndRemovesTheProposal() throws Exception {
        var held = holdBash();
        var proposal = service.pending(session).getFirst();

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
        assertThat(service.pending(session)).hasSize(2);
        assertThat(service.pending(otherSession)).hasSize(1);
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
            while (service.pending().size() < count) {
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
