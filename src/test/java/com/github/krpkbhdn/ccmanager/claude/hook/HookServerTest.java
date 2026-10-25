package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

class HookServerTest {

    private static final int MAX_BODY = 1024;
    private static final long VERIFY_TIMEOUT_MS = 2000;

    private final HookEventStore store = mock(HookEventStore.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final McpEndpoint mcp = mock(McpEndpoint.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final HttpClient client = HttpClient.newHttpClient();
    private HookServer server;

    @BeforeEach
    void setUp() {
        server = new HookServer(store, publisher, JsonMapper.builder().build(),
                new HookProperties(10, MAX_BODY, 500, Duration.ofSeconds(5)), mcp, proposals);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        client.close();
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void acceptsHookCallWithEmptyOkAndPublishesEvent() throws Exception {
        var response = post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"Stop\",\"session_id\":\"c1\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        var captor = ArgumentCaptor.forClass(HookEvent.class);
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(captor.capture());
        assertThat(captor.getValue().ccmSessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().type()).isEqualTo(HookEventType.STOP);
        assertThat(captor.getValue().text("session_id")).isEqualTo("c1");
        verify(store).save(captor.getValue());
    }

    @Test
    void permissionRequestIsHeldUntilDecidedThenAnsweredWithTheDecision() throws Exception {
        var decision = new CompletableFuture<Optional<String>>();
        when(proposals.hold(org.mockito.ArgumentMatchers.any(HookEvent.class))).thenReturn(decision);
        var response = client.sendAsync(HttpRequest.newBuilder(URI.create(server.hookUrl("sess-1")))
                .POST(HttpRequest.BodyPublishers.ofString("{\"hook_event_name\":\"PermissionRequest\",\"tool_name\":\"Bash\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        var captor = ArgumentCaptor.forClass(HookEvent.class);
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(captor.capture());
        verify(store, timeout(VERIFY_TIMEOUT_MS)).save(captor.getValue());
        assertThat(response).isNotDone();
        decision.complete(Optional.of("{\"decided\":true}"));

        var answered = response.get();
        assertThat(answered.statusCode()).isEqualTo(200);
        assertThat(answered.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(answered.body()).isEqualTo("{\"decided\":true}");
    }

    @Test
    void heldCallWithoutDecisionGetsEmptyOk() throws Exception {
        when(proposals.hold(org.mockito.ArgumentMatchers.any(HookEvent.class))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        var response = post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"Elicitation\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void otherHooksNeverTouchTheProposals() throws Exception {
        post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"PreToolUse\"}");

        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(ArgumentCaptor.forClass(HookEvent.class).capture());
        verifyNoInteractions(proposals);
    }

    @Test
    void listensOnLoopbackOnly() {
        assertThat(server.hookUrl("x")).startsWith("http://127.0.0.1:" + server.port() + "/hook/");
    }

    @Test
    void rejectsWrongSecretWithNotFound() throws Exception {
        var url = server.hookUrl("sess-1").replace("/hook/", "/hook/wrong");

        assertThat(post(url, "{\"hook_event_name\":\"Stop\"}").statusCode()).isEqualTo(404);
        verifyNoInteractions(publisher);
    }

    @Test
    void malformedJsonStillAnswersOkAndPublishesNothing() throws Exception {
        var response = post(server.hookUrl("sess-1"), "{not json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"Stop\"}");
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(ArgumentCaptor.forClass(HookEvent.class).capture());
    }

    @Test
    void oversizedBodyIsDroppedButAnsweredOk() throws Exception {
        var big = "{\"hook_event_name\":\"Stop\",\"pad\":\"" + "x".repeat(MAX_BODY) + "\"}";

        assertThat(post(server.hookUrl("sess-1"), big).statusCode()).isEqualTo(200);
        post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"PreCompact\"}");

        var captor = ArgumentCaptor.forClass(HookEvent.class);
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(HookEventType.PRE_COMPACT);
    }

    @Test
    void getRequestIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(server.hookUrl("sess-1"))).GET().build();

        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
    }

    @Test
    void mcpPostIsAnsweredSynchronouslyWithJson() throws Exception {
        when(mcp.handle("sess-1", "{\"id\":1}".getBytes(StandardCharsets.UTF_8))).thenReturn(Optional.of("{\"result\":{}}"));

        var response = post(server.mcpUrl("sess-1"), "{\"id\":1}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(response.body()).isEqualTo("{\"result\":{}}");
    }

    @Test
    void mcpNotificationIsAcceptedWithoutBody() throws Exception {
        when(mcp.handle("sess-1", "{}".getBytes(StandardCharsets.UTF_8))).thenReturn(Optional.empty());

        var response = post(server.mcpUrl("sess-1"), "{}");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void mcpRejectsWrongSecretGetAndOversizedBody() throws Exception {
        assertThat(post(server.mcpUrl("sess-1").replace("/mcp/", "/mcp/wrong"), "{}").statusCode()).isEqualTo(404);
        var get = HttpRequest.newBuilder(URI.create(server.mcpUrl("sess-1"))).GET().build();
        assertThat(client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        assertThat(post(server.mcpUrl("sess-1"), "x".repeat(MAX_BODY + 1)).statusCode()).isEqualTo(413);
        verifyNoInteractions(mcp);
    }
    @Test
    void statusLinePostIsPublishedAsReportWithEmptyOk() throws Exception {
        var response = post(server.statusLineUrl("sess-1"), "{\"rate_limits\":{}}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        var captor = ArgumentCaptor.forClass(StatusLineReport.class);
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(captor.capture());
        assertThat(captor.getValue().ccmSessionId()).isEqualTo("sess-1");
        verifyNoInteractions(store);
    }

    @Test
    void statusLineRejectsWrongSecret() throws Exception {
        var url = server.statusLineUrl("sess-1").replace("/statusline/", "/statusline/wrong");

        assertThat(post(url, "{}").statusCode()).isEqualTo(404);
        verifyNoInteractions(publisher);
    }
}
