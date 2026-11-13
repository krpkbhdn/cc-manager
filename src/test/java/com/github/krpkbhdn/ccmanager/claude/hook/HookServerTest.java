package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.app.mcp.AssistantMcpEndpoint;
import com.github.krpkbhdn.ccmanager.app.mcp.AssistantMcpTool;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final AssistantMcpEndpoint assistantMcp = mock(AssistantMcpEndpoint.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final HttpClient client = HttpClient.newHttpClient();
    private HookServer server;

    @BeforeEach
    void setUp() {
        server = new HookServer(store, publisher, JsonMapper.builder().build(),
                new HookProperties(10, MAX_BODY, 500, Duration.ofSeconds(5), 10, 1000, 1000), mcp, assistantMcp, proposals);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        client.close();
    }

    /** Posts with the credential of the session named by the last path segment of the url. */
    private HttpResponse<String> post(String url, String body) throws Exception {
        return postWithAuthorization(url, body, "Bearer " + server.sessionToken(url.substring(url.lastIndexOf('/') + 1)));
    }

    private HttpResponse<String> postWithAuthorization(String url, String body, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
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
        verify(store, timeout(VERIFY_TIMEOUT_MS)).save(captor.getValue());
    }

    @Test
    void eventIsPublishedWhileStoringIsStillBlocked() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            release.await();
            return null;
        }).when(store).save(any(HookEvent.class));

        post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"Stop\"}");

        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(any(HookEvent.class));
        release.countDown();
    }

    @Test
    void anOversizedBodyStillReachesStatusHandlingByItsEventName() throws Exception {
        var padding = "x".repeat(MAX_BODY * 2);

        post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"Stop\",\"session_id\":\"c1\",\"tool_input\":\"" + padding + "\"}");

        var captor = ArgumentCaptor.forClass(HookEvent.class);
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(HookEventType.STOP);
    }

    @Test
    void anOversizedDecisionRequestIsNeitherHeldNorPublishedAndTheTerminalStaysInCharge() throws Exception {
        var padding = "x".repeat(MAX_BODY * 2);

        var response = post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"PermissionRequest\",\"tool_input\":\"" + padding + "\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(proposals);
        verify(publisher, never()).publishEvent(any(HookEvent.class));
    }

    @Test
    void anOversizedBodyWhoseEventNameLiesBeyondTheCapIsDroppedWithoutFailing() throws Exception {
        var padding = "x".repeat(MAX_BODY * 2);

        var response = post(server.hookUrl("sess-1"), "{\"tool_input\":\"" + padding + "\",\"hook_event_name\":\"Stop\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        verify(publisher, never()).publishEvent(any(HookEvent.class));
    }

    @Test
    void stopFlushesQueuedEventsToTheStoreInArrivalOrder() throws Exception {
        for (var i = 0; i < 5; i++) {
            post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"Stop\",\"session_id\":\"c" + i + "\"}");
        }

        server.stop();

        var captor = ArgumentCaptor.forClass(HookEvent.class);
        verify(store, org.mockito.Mockito.times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(event -> event.text("session_id")).containsExactly("c0", "c1", "c2", "c3", "c4");
    }

    @Test
    void callsBeyondTheQueueBoundAreDroppedWhileTheWorkerIsStuck() throws Exception {
        var bounded = new HookServer(store, publisher, JsonMapper.builder().build(),
                new HookProperties(10, MAX_BODY, 500, Duration.ofSeconds(5), 10, 1000, 2), mcp, assistantMcp, proposals);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            release.await();
            return null;
        }).when(publisher).publishEvent(any(HookEvent.class));
        bounded.start();
        try {
            for (var i = 0; i < 6; i++) {
                assertThat(postWithAuthorization(bounded.hookUrl("sess-1"), "{\"hook_event_name\":\"PreToolUse\"}",
                        "Bearer " + bounded.sessionToken("sess-1")).statusCode()).isEqualTo(200);
            }
            release.countDown();

            verify(publisher, timeout(VERIFY_TIMEOUT_MS).atLeast(3)).publishEvent(any(HookEvent.class));
            org.mockito.Mockito.verify(publisher, org.mockito.Mockito.atMost(4)).publishEvent(any(HookEvent.class));
        } finally {
            release.countDown();
            bounded.stop();
        }
    }

    @Test
    void aStatusEndingEventWaitsForRoomAndStaysBehindQueuedEventsOfItsSession() throws Exception {
        var bounded = boundedServer();
        var release = new java.util.concurrent.CountDownLatch(1);
        var names = new java.util.concurrent.CopyOnWriteArrayList<String>();
        org.mockito.Mockito.doAnswer(call -> {
            names.add(call.<HookEvent>getArgument(0).text("hook_event_name"));
            release.await();
            return null;
        }).when(publisher).publishEvent(any(HookEvent.class));
        bounded.start();
        try {
            for (var name : new String[] {"PreToolUse", "PreToolUse", "PreToolUse", "PreToolUse", "Stop"}) {
                assertThat(postWithAuthorization(bounded.hookUrl("sess-1"), "{\"hook_event_name\":\"" + name + "\"}",
                        "Bearer " + bounded.sessionToken("sess-1")).statusCode()).isEqualTo(200);
            }
            release.countDown();

            org.awaitility.Awaitility.await().atMost(Duration.ofMillis(VERIFY_TIMEOUT_MS)).until(() -> names.contains("Stop"));
            assertThat(names.getLast()).isEqualTo("Stop");
        } finally {
            release.countDown();
            bounded.stop();
        }
    }

    @Test
    void nothingRunsAfterStopEvenForAStatusEndingEventStillWaitingForRoom() throws Exception {
        var bounded = boundedServer();
        var release = new java.util.concurrent.CountDownLatch(1);
        var names = new java.util.concurrent.CopyOnWriteArrayList<String>();
        org.mockito.Mockito.doAnswer(call -> {
            names.add(call.<HookEvent>getArgument(0).text("hook_event_name"));
            release.await();
            return null;
        }).when(publisher).publishEvent(any(HookEvent.class));
        bounded.start();
        for (var name : new String[] {"PreToolUse", "PreToolUse", "PreToolUse", "PreToolUse", "Stop"}) {
            postWithAuthorization(bounded.hookUrl("sess-1"), "{\"hook_event_name\":\"" + name + "\"}",
                    "Bearer " + bounded.sessionToken("sess-1"));
        }

        bounded.stop();
        release.countDown();

        org.awaitility.Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofMillis(VERIFY_TIMEOUT_MS))
                .untilAsserted(() -> assertThat(names).doesNotContain("Stop"));
    }

    @Test
    void anEventOfTheSessionAfterAStatusEndingEventWaitingForRoomStaysBehindIt() throws Exception {
        var bounded = boundedServer();
        var release = new java.util.concurrent.CountDownLatch(1);
        var names = new java.util.concurrent.CopyOnWriteArrayList<String>();
        org.mockito.Mockito.doAnswer(call -> {
            names.add(call.<HookEvent>getArgument(0).text("hook_event_name"));
            release.await();
            return null;
        }).when(publisher).publishEvent(any(HookEvent.class));
        bounded.start();
        try {
            for (var name : new String[] {"PreToolUse", "PreToolUse", "PreToolUse", "PreToolUse", "Stop", "UserPromptSubmit"}) {
                postWithAuthorization(bounded.hookUrl("sess-1"), "{\"hook_event_name\":\"" + name + "\"}",
                        "Bearer " + bounded.sessionToken("sess-1"));
            }
            release.countDown();

            org.awaitility.Awaitility.await().atMost(Duration.ofMillis(VERIFY_TIMEOUT_MS)).until(() -> names.contains("UserPromptSubmit"));
            assertThat(names.indexOf("Stop")).isGreaterThanOrEqualTo(0).isLessThan(names.indexOf("UserPromptSubmit"));
        } finally {
            release.countDown();
            bounded.stop();
        }
    }

    private HookServer boundedServer() {
        return new HookServer(store, publisher, JsonMapper.builder().build(),
                new HookProperties(10, MAX_BODY, 500, Duration.ofSeconds(5), 10, 1000, 2), mcp, assistantMcp, proposals);
    }

    @Test
    void permissionRequestIsHeldUntilDecidedThenAnsweredWithTheDecision() throws Exception {
        var decision = new CompletableFuture<Optional<String>>();
        when(proposals.hold(org.mockito.ArgumentMatchers.any(HookEvent.class))).thenReturn(decision);
        var response = client.sendAsync(HttpRequest.newBuilder(URI.create(server.hookUrl("sess-1")))
                .header("Authorization", "Bearer " + server.sessionToken("sess-1")).POST(HttpRequest.BodyPublishers.ofString("{\"hook_event_name\":\"PermissionRequest\",\"tool_name\":\"Bash\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        var captor = ArgumentCaptor.forClass(HookEvent.class);
        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(captor.capture());
        verify(store, timeout(VERIFY_TIMEOUT_MS)).save(captor.getValue());
        assertThat(response).isNotDone();
        decision.complete(Optional.of("{\"decided\":true}"));

        var answered = response.get(VERIFY_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
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
    void stopClosesTheListenerBeforeReleasingHeldProposals() {
        var url = server.hookUrl("sess-1");
        var listenerOpenDuringRelease = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            try {
                post(url, "{\"hook_event_name\":\"PermissionRequest\"}");
                listenerOpenDuringRelease.set(true);
            } catch (java.io.IOException refused) {
                listenerOpenDuringRelease.set(false);
            }
            return null;
        }).when(proposals).releaseAll();

        server.stop();

        verify(proposals).releaseAll();
        assertThat(listenerOpenDuringRelease).isFalse();
    }

    @Test
    void otherHooksNeverTouchTheProposals()throws Exception {
        post(server.hookUrl("sess-1"), "{\"hook_event_name\":\"PreToolUse\"}");

        verify(publisher, timeout(VERIFY_TIMEOUT_MS)).publishEvent(ArgumentCaptor.forClass(HookEvent.class).capture());
        verifyNoInteractions(proposals);
    }

    @Test
    void listensOnLoopbackOnly() {
        assertThat(server.hookUrl("x")).startsWith("http://127.0.0.1:" + server.port() + "/hook/");
    }

    @Test
    void credentialOfOneSessionDoesNotAuthorizeAnother() throws Exception {
        var stolen = "Bearer " + server.sessionToken("sess-1");

        assertThat(server.sessionToken("sess-1")).isNotEqualTo(server.sessionToken("sess-2"));
        assertThat(postWithAuthorization(server.hookUrl("sess-2"), "{\"hook_event_name\":\"Stop\"}", stolen).statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(server.statusLineUrl("sess-2"), "{}", stolen).statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(server.mcpUrl("sess-2"), "{}", stolen).statusCode()).isEqualTo(404);
        assertThat(post(server.hookUrl("sess-2"), "{\"hook_event_name\":\"Stop\"}").statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsMissingWrongOrUrlSecretWithNotFound() throws Exception {
        var url = server.hookUrl("sess-1");
        var token = server.sessionToken("sess-1");
        var body = "{\"hook_event_name\":\"Stop\"}";

        assertThat(postWithAuthorization(url, body, "Bearer wrong").statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url, body, token).statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url, body, "Bearer ").statusCode()).isEqualTo(404);
        assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url.replace("/hook/", "/hook/" + token + "/"), body, "Bearer " + token).statusCode()).isEqualTo(404);
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
    void getRequestIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(server.hookUrl("sess-1")))
                .header("Authorization", "Bearer " + server.sessionToken("sess-1")).GET().build();

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
        assertThat(postWithAuthorization(server.mcpUrl("sess-1"), "{}", "Bearer wrong").statusCode()).isEqualTo(404);
        var get = HttpRequest.newBuilder(URI.create(server.mcpUrl("sess-1")))
                .header("Authorization", "Bearer " + server.sessionToken("sess-1")).GET().build();
        assertThat(client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        assertThat(post(server.mcpUrl("sess-1"), "x".repeat(MAX_BODY + 1)).statusCode()).isEqualTo(413);
        verifyNoInteractions(mcp);
    }

    private HttpResponse<String> postWithAuthorization(String url, String authorization) throws Exception {
        return postWithAuthorization(url, "{}", authorization);
    }

    @Test
    void assistantMcpAcceptsOnlyItsBearerToken() throws Exception {
        when(assistantMcp.handle("{}".getBytes(StandardCharsets.UTF_8))).thenReturn(Optional.of("{\"result\":{}}"));
        var url = server.assistantMcpUrl();
        var token = server.assistantMcpToken();

        var response = postWithAuthorization(url, "Bearer " + token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"result\":{}}");
        assertThat(url).doesNotContain(token);
        assertThat(postWithAuthorization(url, "Bearer " + token + "x").statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url, "Bearer wrong").statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url, token).statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url, "Bearer ").statusCode()).isEqualTo(404);
        assertThat(post(url, "{}").statusCode()).isEqualTo(404);
        assertThat(post(url + "/" + token, "{}").statusCode()).isEqualTo(404);
        assertThat(postWithAuthorization(url + "/" + token, "Bearer " + token).statusCode()).isEqualTo(404);
        var projectSecret = server.sessionToken("sess-1");
        assertThat(postWithAuthorization(url, "Bearer " + projectSecret).statusCode()).isEqualTo(404);
    }

    @Test
    void assistantMcpSecretAloneDoesNotRunAToolThatNeedsApproval() throws Exception {
        var tool = mock(AssistantMcpTool.class);
        when(tool.name()).thenReturn("git_push");
        var real = new AssistantMcpEndpoint(List.of(tool), JsonMapper.builder().build(), proposals, mock(SessionRegistry.class));
        var secured = new HookServer(store, publisher, JsonMapper.builder().build(),
                new HookProperties(10, MAX_BODY, 500, Duration.ofSeconds(5), 10, 1000, 1000), mcp, real, proposals);
        secured.start();
        try {
            var call = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"git_push\",\"arguments\":{}}}";

            var request = HttpRequest.newBuilder(URI.create(secured.assistantMcpUrl()))
                    .header("Authorization", "Bearer " + secured.assistantMcpToken())
                    .POST(HttpRequest.BodyPublishers.ofString(call)).build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.body()).contains("\"isError\":true");
            verify(tool, never()).call(any());
        } finally {
            secured.stop();
        }
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
        assertThat(postWithAuthorization(server.statusLineUrl("sess-1"), "{}", "Bearer wrong").statusCode()).isEqualTo(404);
        verifyNoInteractions(publisher);
    }

    @Test
    void startAndStopLeaveNoLiveWorkerThread() {
        server.stop();
        for (var i = 0; i < 3; i++) {
            server.start();
            server.stop();
        }
        server.start();
        server.stop();
        assertThat(Thread.getAllStackTraces().keySet()).noneMatch(t -> t.getName().equals("hook-worker") && t.isAlive());
    }
}
