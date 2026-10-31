package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.hook.HookProperties;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.Denial;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService;
import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AssistantMcpEndpointTest {

    private static final int WAIT_SECONDS = 2;
    private static final Duration SHORT_HOLD = Duration.ofMillis(150);
    private static final String CALL_PUSH = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"git_push\",\"arguments\":{\"project\":1}}}";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AssistantMcpTool tool = mock(AssistantMcpTool.class);
    private final AssistantMcpTool destructive = mock(AssistantMcpTool.class);
    private final SessionRegistry registry = new SessionRegistry(new SessionProperties(Duration.ofMinutes(10), Duration.ofMinutes(10)), mock(TranscriptService.class));
    private ProposalService proposals = proposalsHolding(Duration.ofSeconds(30));
    private AssistantMcpEndpoint endpoint;

    private ProposalService proposalsHolding(Duration hold) {
        return new ProposalService(registry, new HookProperties(10, 1024, 500, hold, 1000, 1000));
    }

    @BeforeEach
    void setUp() {
        when(tool.name()).thenReturn("list_projects");
        when(tool.description()).thenReturn("Lists.");
        when(tool.inputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tool.autoApproved()).thenReturn(true);
        when(tool.call(any())).thenReturn("[]");
        when(destructive.name()).thenReturn("git_push");
        when(destructive.description()).thenReturn("Pushes.");
        when(destructive.inputSchema()).thenReturn("{\"type\":\"object\"}");
        when(destructive.call(any())).thenReturn("pushed");
        rebuildEndpoint();
    }

    private void rebuildEndpoint() {
        endpoint = new AssistantMcpEndpoint(List.of(tool, destructive), mapper, proposals, registry);
    }

    private String startAssistantSession() {
        var id = registry.register(ClaudeLauncher.ASSISTANT_PROJECT_ID);
        registry.on(new HookEvent(id, HookEventType.PRE_TOOL_USE, "PreToolUse", mapper.readTree("{}"), Instant.now()));
        return id;
    }

    private JsonNode send(String json) {
        return mapper.readTree(endpoint.handle(json.getBytes(StandardCharsets.UTF_8)).orElseThrow());
    }

    private CompletableFuture<JsonNode> sendAsync(String json) {
        return CompletableFuture.supplyAsync(() -> send(json));
    }

    private List<PermissionProposal> awaitPending(int count) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (proposals.pending().size() < count && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(proposals.pending()).hasSize(count);
        return proposals.pending().stream().map(PermissionProposal.class::cast).toList();
    }

    private JsonNode resultOf(CompletableFuture<JsonNode> response) throws Exception {
        return response.get(WAIT_SECONDS, TimeUnit.SECONDS).path("result");
    }

    private static String textOf(JsonNode result) {
        return result.path("content").get(0).path("text").asString();
    }

    @Test
    void initializeNamesTheAppServerAndCarriesItsInstructions() {
        var result = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}").path("result");

        assertThat(result.path("serverInfo").path("name").asString()).isEqualTo("cc-manager-app");
        assertThat(result.path("instructions").asString()).isEqualTo(AssistantMcpEndpoint.INSTRUCTIONS);
    }

    @Test
    void listsOnlyTheAssistantTools() {
        var tools = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").path("result").path("tools");

        assertThat(tools.size()).isEqualTo(2);
        assertThat(tools.get(0).path("name").asString()).isEqualTo("list_projects");
    }

    @Test
    void autoApprovedToolRunsWithoutASessionOrProposal() {
        var result = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_projects\",\"arguments\":{}}}").path("result");

        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(textOf(result)).isEqualTo("[]");
        assertThat(proposals.pending()).isEmpty();
    }

    @Test
    void unknownToolIsAnError() {
        var response = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"set_run_configuration\"}}");

        assertThat(response.path("error").path("message").asString()).startsWith("Unknown tool");
    }

    @Test
    void destructiveToolDoesNothingUntilTheUserAllowsIt() throws Exception {
        var assistant = startAssistantSession();
        var response = sendAsync(CALL_PUSH);

        var proposal = awaitPending(1).getFirst();
        verify(destructive, never()).call(any());
        assertThat(proposal.ccmSessionId()).isEqualTo(assistant);
        assertThat(proposal.toolName()).isEqualTo("mcp__cc-manager-app__git_push");
        assertThat(proposal.agentId()).isEmpty();
        assertThat(proposal.toolInput().path("project").asInt()).isEqualTo(1);
        assertThat(response).isNotDone();

        proposals.answer(proposal.id(), PermissionAllow.once());

        assertThat(textOf(resultOf(response))).isEqualTo("pushed");
    }

    @Test
    void denialAndDismissNeverCallTheTool() throws Exception {
        startAssistantSession();
        var denied = sendAsync(CALL_PUSH);
        proposals.answer(awaitPending(1).getFirst().id(), new Denial(""));
        var dismissed = sendAsync(CALL_PUSH);
        proposals.dismiss(awaitPending(1).getFirst().id());

        assertThat(resultOf(denied).path("isError").asBoolean()).isTrue();
        assertThat(textOf(resultOf(denied))).isEqualTo(AssistantMcpEndpoint.DENIED);
        assertThat(resultOf(dismissed).path("isError").asBoolean()).isTrue();
        assertThat(textOf(resultOf(dismissed))).isEqualTo(AssistantMcpEndpoint.RELEASED);
        verify(destructive, never()).call(any());
    }

    @Test
    void unansweredRequestTimesOutWithoutCallingTheTool() throws Exception {
        proposals = proposalsHolding(SHORT_HOLD);
        rebuildEndpoint();
        startAssistantSession();

        var result = resultOf(sendAsync(CALL_PUSH));

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(textOf(result)).isEqualTo(AssistantMcpEndpoint.TIMED_OUT);
        verify(destructive, never()).call(any());
    }

    @Test
    void withoutALiveAssistantSessionTheToolIsRefused() throws Exception {
        registry.register(7L);

        var result = resultOf(sendAsync(CALL_PUSH));

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(textOf(result)).isEqualTo(AssistantMcpEndpoint.NO_ASSISTANT);
        assertThat(proposals.pending()).isEmpty();
        verify(destructive, never()).call(any());
    }

    @Test
    void twoParallelCallsHoldTwoProposals() throws Exception {
        startAssistantSession();
        var first = sendAsync(CALL_PUSH);
        var second = sendAsync(CALL_PUSH);

        var held = awaitPending(2);

        assertThat(held.get(0).id()).isNotEqualTo(held.get(1).id());
        held.forEach(proposal -> proposals.answer(proposal.id(), PermissionAllow.once()));
        assertThat(resultOf(first).path("isError").asBoolean()).isFalse();
        assertThat(resultOf(second).path("isError").asBoolean()).isFalse();
    }
}
