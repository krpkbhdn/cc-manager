package com.github.krpkbhdn.ccmanager.claude.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpEndpointTest {

    private static final long PROJECT_ID = 7;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final McpTool tool = mock(McpTool.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private McpEndpoint endpoint;

    @BeforeEach
    void setUp() {
        when(tool.name()).thenReturn("echo");
        when(tool.description()).thenReturn("Echoes.");
        when(tool.inputSchema()).thenReturn("{\"type\":\"object\"}");
        var session = mock(SessionSnapshot.class);
        when(session.projectId()).thenReturn(PROJECT_ID);
        when(sessions.find("s1")).thenReturn(Optional.of(session));
        endpoint = new McpEndpoint(List.of(tool), sessions, mapper);
    }

    private Optional<String> send(String sessionId, String json) {
        return endpoint.handle(sessionId, json.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode call(String json) {
        return mapper.readTree(send("s1", json).orElseThrow());
    }

    @Test
    void initializeEchoesASupportedVersionAndDeclaresTools() {
        var result = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}").path("result");

        assertThat(result.path("protocolVersion").asString()).isEqualTo("2025-06-18");
        assertThat(result.path("capabilities").has("tools")).isTrue();
        assertThat(result.path("serverInfo").path("name").asString()).isEqualTo("cc-manager");
        assertThat(result.path("instructions").asString()).contains("run-configurations");
    }

    @Test
    void initializeAnswersTheNewestVersionForAnUnknownOne() {
        var result = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}").path("result");

        assertThat(result.path("protocolVersion").asString()).isEqualTo("2025-11-25");
    }

    @Test
    void toolsListDescribesEveryTool() {
        var tools = call("{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"tools/list\"}").path("result").path("tools");

        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).path("name").asString()).isEqualTo("echo");
        assertThat(tools.get(0).path("inputSchema").path("type").asString()).isEqualTo("object");
    }

    @Test
    void toolCallRunsTheToolForTheProjectOfTheSession() {
        when(tool.call(PROJECT_ID, mapper.readTree("{\"x\":1}"))).thenReturn("done");

        var response = call("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"x\":1}}}");

        assertThat(response.path("id").asInt()).isEqualTo(3);
        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.path("result").path("content").get(0).path("text").asString()).isEqualTo("done");
    }

    @Test
    void toolFailureBecomesAnErrorResultNotAProtocolError() {
        when(tool.call(anyLong(), any())).thenThrow(new IllegalArgumentException("bad name"));

        var result = call("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}").path("result");

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asString()).isEqualTo("bad name");
    }

    @Test
    void unknownSessionIsAToolError() {
        var result = mapper.readTree(send("gone", "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}").orElseThrow())
                .path("result");

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asString()).contains("not managed by cc-manager anymore");
    }

    @Test
    void unknownToolIsInvalidParams() {
        assertThat(call("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}").path("error").path("code").asInt())
                .isEqualTo(-32602);
    }

    @Test
    void notificationGetsNoResponse() {
        assertThat(send("s1", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).isEmpty();
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        assertThat(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/list\"}").path("error").path("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void pingAnswersAnEmptyResult() {
        assertThat(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").path("result").size()).isZero();
    }

    @Test
    void malformedJsonIsAParseErrorWithNullId() {
        var response = call("{not json");

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(response.path("id").isNull()).isTrue();
    }
}
