package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
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
    private final HttpClient client = HttpClient.newHttpClient();
    private HookServer server;

    @BeforeEach
    void setUp() {
        server = new HookServer(store, publisher, JsonMapper.builder().build(), new HookProperties(10, MAX_BODY, 500));
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
}
