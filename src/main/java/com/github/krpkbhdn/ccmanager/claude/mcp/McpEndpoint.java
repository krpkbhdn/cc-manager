package com.github.krpkbhdn.ccmanager.claude.mcp;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stateless MCP server (JSON-RPC 2.0 over streamable HTTP POST) that cc-manager hands to every session it launches.
 * Answers a request with its JSON response and a notification with nothing.
 */
@Component
public class McpEndpoint {

    static final List<String> SUPPORTED_VERSIONS = List.of("2025-03-26", "2025-06-18", "2025-11-25");
    static final String SERVER_NAME = "cc-manager";
    static final String SERVER_VERSION = "1.0.0";
    static final String INSTRUCTIONS = "This server belongs to Claude Code Manager, the desktop app running this session. "
            + "Use its run configuration tools so the user can start the app from cc-manager. "
            + "See the cc-manager:run-configurations skill for when to use them.";
    private static final Logger log = LoggerFactory.getLogger(McpEndpoint.class);
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;

    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private final SessionRegistry sessions;
    private final JsonMapper mapper;

    public McpEndpoint(List<McpTool> tools, SessionRegistry sessions, JsonMapper mapper) {
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
        this.sessions = sessions;
        this.mapper = mapper;
    }

    /** @return the JSON response, empty for a notification */
    public Optional<String> handle(String ccmSessionId, byte[] body) {
        JsonNode request;
        try {
            request = mapper.readTree(body);
        } catch (RuntimeException e) {
            return Optional.of(error(null, PARSE_ERROR, "Parse error"));
        }
        var id = request.get("id");
        log.debug("MCP JSON-RPC {} (id {})", request.path("method").asString(""), id);
        if (!request.isObject() || !request.path("method").isString()) {
            return id == null && request.isObject() ? Optional.empty() : Optional.of(error(id, INVALID_REQUEST, "Invalid request"));
        }
        if (id == null) {
            return Optional.empty();
        }
        try {
            var response = mapper.createObjectNode().put("jsonrpc", "2.0");
            response.set("id", id);
            response.set("result", dispatch(ccmSessionId, request.path("method").asString(), request.path("params")));
            return Optional.of(mapper.writeValueAsString(response));
        } catch (RpcException e) {
            return Optional.of(error(id, e.code, e.getMessage()));
        }
    }

    private JsonNode dispatch(String ccmSessionId, String method, JsonNode params) {
        return switch (method) {
            case "initialize" -> initialize(params);
            case "ping" -> mapper.createObjectNode();
            case "tools/list" -> listTools();
            case "tools/call" -> callTool(ccmSessionId, params);
            default -> throw new RpcException(METHOD_NOT_FOUND, "Method not found: " + method);
        };
    }

    private ObjectNode initialize(JsonNode params) {
        var requested = params.path("protocolVersion").asString("");
        var result = mapper.createObjectNode();
        result.put("protocolVersion", SUPPORTED_VERSIONS.contains(requested) ? requested : SUPPORTED_VERSIONS.getLast());
        result.putObject("capabilities").putObject("tools");
        result.putObject("serverInfo").put("name", SERVER_NAME).put("version", SERVER_VERSION);
        result.put("instructions", INSTRUCTIONS);
        return result;
    }

    private ObjectNode listTools() {
        var result = mapper.createObjectNode();
        var list = result.putArray("tools");
        tools.values().forEach(tool -> {
            var entry = list.addObject().put("name", tool.name()).put("description", tool.description());
            entry.set("inputSchema", mapper.readTree(tool.inputSchema()));
        });
        return result;
    }

    private ObjectNode callTool(String ccmSessionId, JsonNode params) {
        var tool = tools.get(params.path("name").asString(""));
        if (tool == null) {
            throw new RpcException(INVALID_PARAMS, "Unknown tool: " + params.path("name").asString(""));
        }
        try {
            var session = sessions.find(ccmSessionId)
                    .orElseThrow(() -> new IllegalStateException("This session is not managed by cc-manager anymore"));
            return toolResult(tool.call(session.projectId(), params.path("arguments")), false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return toolResult(e.getMessage(), true);
        } catch (RuntimeException e) {
            log.warn("MCP tool {} failed", tool.name(), e);
            return toolResult("cc-manager could not run " + tool.name() + ": " + e.getClass().getSimpleName(), true);
        }
    }

    private ObjectNode toolResult(String text, boolean isError) {
        var result = mapper.createObjectNode();
        result.putArray("content").addObject().put("type", "text").put("text", text);
        result.put("isError", isError);
        return result;
    }

    private String error(JsonNode id, int code, String message) {
        var response = mapper.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        response.putObject("error").put("code", code).put("message", message);
        return mapper.writeValueAsString(response);
    }

    private static final class RpcException extends RuntimeException {
        private final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
