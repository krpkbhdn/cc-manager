package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AssistantMcpEndpointTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AssistantMcpTool tool = mock(AssistantMcpTool.class);
    private AssistantMcpEndpoint endpoint;

    @BeforeEach
    void setUp() {
        when(tool.name()).thenReturn("list_projects");
        when(tool.description()).thenReturn("Lists.");
        when(tool.inputSchema()).thenReturn("{\"type\":\"object\"}");
        when(tool.call(any())).thenReturn("[]");
        endpoint = new AssistantMcpEndpoint(List.of(tool), mapper);
    }

    private JsonNode send(String json) {
        return mapper.readTree(endpoint.handle(json.getBytes(StandardCharsets.UTF_8)).orElseThrow());
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

        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).path("name").asString()).isEqualTo("list_projects");
    }

    @Test
    void callsAToolWithoutASession() {
        var result = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_projects\",\"arguments\":{}}}").path("result");

        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("content").get(0).path("text").asString()).isEqualTo("[]");
    }

    @Test
    void unknownToolIsAnError() {
        var response = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"set_run_configuration\"}}");

        assertThat(response.path("error").path("message").asString()).startsWith("Unknown tool");
    }
}
