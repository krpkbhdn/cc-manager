package com.github.krpkbhdn.ccmanager.claude.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpJsonRpcServerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private record Tool(String name) implements McpToolDescriptor {
        @Override
        public String description() {
            return "does " + name;
        }

        @Override
        public String inputSchema() {
            return "{\"type\":\"object\"}";
        }
    }

    private static final class Server extends McpJsonRpcServer<Tool> {
        Server(List<Tool> tools) {
            super(tools, MAPPER, "test-server", "be nice");
        }

        Optional<String> post(String body) {
            return respond(body.getBytes(StandardCharsets.UTF_8), (tool, arguments) -> {
                return switch (tool.name()) {
                    case "echo" -> "echo:" + arguments.path("v").asString("");
                    case "bad-arg" -> throw new IllegalArgumentException("fix the argument");
                    case "bad-state" -> throw new IllegalStateException("not now");
                    default -> throw new UnsupportedOperationException("secret detail");
                };
            });
        }
    }

    private final Server server = new Server(List.of(new Tool("echo"), new Tool("bad-arg"), new Tool("bad-state"), new Tool("crash")));

    private JsonNode reply(String body) {
        return MAPPER.readTree(server.post(body).orElseThrow());
    }

    @Test
    void initializeEchoesASupportedProtocolVersionAndDescribesTheServer() {
        var result = reply("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}");

        assertThat(result.path("id").asInt()).isEqualTo(1);
        assertThat(result.path("result").path("protocolVersion").asString()).isEqualTo("2025-06-18");
        assertThat(result.path("result").path("serverInfo").path("name").asString()).isEqualTo("test-server");
        assertThat(result.path("result").path("instructions").asString()).isEqualTo("be nice");
        assertThat(result.path("result").path("capabilities").has("tools")).isTrue();
    }

    @Test
    void initializeFallsBackToTheNewestVersionForAnUnknownOrMissingOne() {
        var unknown = reply("{\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}");
        var missing = reply("{\"id\":2,\"method\":\"initialize\"}");

        assertThat(unknown.path("result").path("protocolVersion").asString()).isEqualTo("2025-11-25");
        assertThat(missing.path("result").path("protocolVersion").asString()).isEqualTo("2025-11-25");
    }

    @Test
    void pingReturnsAnEmptyResultAndKeepsAStringId() {
        var result = reply("{\"id\":\"abc\",\"method\":\"ping\"}");

        assertThat(result.path("id").asString()).isEqualTo("abc");
        assertThat(result.path("result").isObject()).isTrue();
        assertThat(result.path("result").size()).isZero();
    }

    @Test
    void toolsListNamesEveryToolWithItsSchemaInRegistrationOrder() {
        var tools = reply("{\"id\":1,\"method\":\"tools/list\"}").path("result").path("tools");

        assertThat(tools.size()).isEqualTo(4);
        assertThat(tools.get(0).path("name").asString()).isEqualTo("echo");
        assertThat(tools.get(0).path("description").asString()).isEqualTo("does echo");
        assertThat(tools.get(0).path("inputSchema").path("type").asString()).isEqualTo("object");
        assertThat(tools.get(3).path("name").asString()).isEqualTo("crash");
    }

    @Test
    void toolsCallReturnsTheToolTextAsASuccessfulResult() {
        var result = reply("{\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"v\":\"hi\"}}}").path("result");

        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("content").get(0).path("type").asString()).isEqualTo("text");
        assertThat(result.path("content").get(0).path("text").asString()).isEqualTo("echo:hi");
    }

    @Test
    void toolsCallWithoutArgumentsPassesAnEmptyArgumentsNode() {
        var result = reply("{\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}").path("result");

        assertThat(result.path("content").get(0).path("text").asString()).isEqualTo("echo:");
    }

    @Test
    void illegalArgumentAndStateMessagesReachClaudeAsToolErrors() {
        var argument = reply("{\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"bad-arg\"}}");
        var state = reply("{\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"bad-state\"}}");

        assertThat(argument.path("result").path("isError").asBoolean()).isTrue();
        assertThat(argument.path("result").path("content").get(0).path("text").asString()).isEqualTo("fix the argument");
        assertThat(state.path("result").path("isError").asBoolean()).isTrue();
        assertThat(state.path("result").path("content").get(0).path("text").asString()).isEqualTo("not now");
    }

    @Test
    void unexpectedToolFailureIsAnErrorResultThatDoesNotLeakTheMessage() {
        var result = reply("{\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"crash\"}}").path("result");

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asString())
                .isEqualTo("cc-manager could not run crash: UnsupportedOperationException").doesNotContain("secret");
    }

    @Test
    void unknownToolIsAnInvalidParamsRpcError() {
        var error = reply("{\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}").path("error");

        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asString()).isEqualTo("Unknown tool: nope");
    }

    @Test
    void toolsCallWithoutParamsIsAnInvalidParamsError() {
        var error = reply("{\"id\":1,\"method\":\"tools/call\"}").path("error");

        assertThat(error.path("code").asInt()).isEqualTo(-32602);
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        var response = reply("{\"id\":7,\"method\":\"resources/list\"}");

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(response.path("error").path("message").asString()).isEqualTo("Method not found: resources/list");
        assertThat(response.path("id").asInt()).isEqualTo(7);
    }

    @Test
    void malformedJsonIsAParseErrorWithNullId() {
        var response = reply("{not json");

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(response.path("id").isNull()).isTrue();
    }

    @Test
    void emptyBodyIsAParseError() {
        assertThat(reply("").path("error").path("code").asInt()).isEqualTo(-32700);
    }

    @Test
    void notificationWithoutIdGetsNoResponse() {
        assertThat(server.post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).isEmpty();
        assertThat(server.post("{\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}")).isEmpty();
    }

    @Test
    void requestWithoutAStringMethodIsInvalidWhenItHasAnId() {
        assertThat(reply("{\"id\":1}").path("error").path("code").asInt()).isEqualTo(-32600);
        assertThat(reply("{\"id\":1,\"method\":5}").path("error").path("code").asInt()).isEqualTo(-32600);
    }

    @Test
    void jsonThatIsNotAnObjectIsInvalidRequest() {
        assertThat(reply("[1,2]").path("error").path("code").asInt()).isEqualTo(-32600);
        assertThat(reply("42").path("error").path("code").asInt()).isEqualTo(-32600);
    }

    @Test
    void objectWithoutMethodAndWithoutIdIsIgnored() {
        assertThat(server.post("{}")).isEmpty();
    }

    @Test
    void duplicateToolNamesAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new Server(List.of(new Tool("a"), new Tool("a"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate MCP tool name: a");
    }
}
