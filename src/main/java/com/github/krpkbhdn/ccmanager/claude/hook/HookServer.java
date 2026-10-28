package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.app.mcp.AssistantMcpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loopback-only HTTP endpoint that Claude Code http hooks (and a curl command hook for SessionStart) post to.
 * Parses each call into a HookEvent, persists it and publishes it (the session registry listens).
 * Answers hooks with 200 and an empty body at once; only PermissionRequest and Elicitation are held until the user answers in the app
 * (or the terminal settles it) and then get a decision. The MCP path answers with content too.
 */
@Component
public class HookServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(HookServer.class);
    private static final String CONTEXT_PATH = "/hook/";
    private static final String STATUS_LINE_CONTEXT_PATH = "/statusline/";
    private static final String MCP_CONTEXT_PATH = "/mcp/";
    private static final String ASSISTANT_MCP_CONTEXT_PATH = "/assistant-mcp/";
    private static final int SECRET_BYTES = 32;
    private static final int OK = 200;
    private static final int ACCEPTED = 202;
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int NO_RESPONSE_BODY = -1;
    private static final int PAYLOAD_TOO_LARGE = 413;
    private static final int PATH_PARTS = 2;

    private final HookEventStore store;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper mapper;
    private final HookProperties properties;
    private final McpEndpoint mcp;
    private final AssistantMcpEndpoint assistantMcp;
    private final ProposalService proposals;
    private final byte[] secret = secret();
    private final byte[] assistantSecret = secret();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("hook-worker").daemon().factory());
    private HttpServer server;

    public HookServer(HookEventStore store, ApplicationEventPublisher publisher, JsonMapper mapper, HookProperties properties,
            McpEndpoint mcp, AssistantMcpEndpoint assistantMcp, ProposalService proposals) {
        this.store = store;
        this.publisher = publisher;
        this.mapper = mapper;
        this.properties = properties;
        this.mcp = mcp;
        this.assistantMcp = assistantMcp;
        this.proposals = proposals;
    }

    public synchronized int port() {
        if (server == null) {
            throw new IllegalStateException("Hook server is not running");
        }
        return server.getAddress().getPort();
    }

    /** URL a hook of the given ccm session posts to; contains the per-run secret. */
    public String hookUrl(String ccmSessionId) {
        return "http://127.0.0.1:" + port() + CONTEXT_PATH + new String(secret, StandardCharsets.US_ASCII) + "/" + ccmSessionId;
    }

    /** URL of the MCP server a session talks to; same secret, own path because it answers instead of just accepting. */
    public String mcpUrl(String ccmSessionId) {
        return hookUrl(ccmSessionId).replace(CONTEXT_PATH, MCP_CONTEXT_PATH);
    }

    /** URL of the app MCP server of the Assistant session; its own secret, never written into project session files. */
    public String assistantMcpUrl() {
        return "http://127.0.0.1:" + port() + ASSISTANT_MCP_CONTEXT_PATH + new String(assistantSecret, StandardCharsets.US_ASCII);
    }

    /** URL a session's statusLine command posts to; same secret, own path because the payload is no hook event. */
    public String statusLineUrl(String ccmSessionId) {
        return hookUrl(ccmSessionId).replace(CONTEXT_PATH, STATUS_LINE_CONTEXT_PATH);
    }

    @Override
    public synchronized void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start hook server", e);
        }
        server.createContext(CONTEXT_PATH, exchange -> serve(exchange, CONTEXT_PATH,
                (ex, id, body) -> respondToHook(ex, id, body, this::acceptHook)));
        server.createContext(STATUS_LINE_CONTEXT_PATH, exchange -> serve(exchange, STATUS_LINE_CONTEXT_PATH,
                (ex, id, body) -> respondToHook(ex, id, body, this::acceptStatusLine)));
        server.createContext(MCP_CONTEXT_PATH, exchange -> serve(exchange, MCP_CONTEXT_PATH, (ex, id, body) -> respondToMcp(ex, bytes -> mcp.handle(id, bytes), body)));
        server.createContext(ASSISTANT_MCP_CONTEXT_PATH, this::serveAssistantMcp);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        log.info("Hook server listening on 127.0.0.1:{}", server.getAddress().getPort());
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        worker.shutdown();
    }

    @Override
    public synchronized boolean isRunning() {
        return server != null;
    }

    private void serve(HttpExchange exchange, String contextPath, BodyResponder responder) throws IOException {
        try (exchange) {
            var ccmSessionId = authorizedSessionId(exchange, contextPath);
            log.debug("{} {}<secret>/{} accept={} mcp-session-id={}", exchange.getRequestMethod(), contextPath,
                    ccmSessionId.orElse("?"), exchange.getRequestHeaders().getFirst("Accept"),
                    exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
            if (ccmSessionId.isEmpty()) {
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
            } else if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, NO_RESPONSE_BODY);
            } else {
                responder.respond(exchange, ccmSessionId.get(), exchange.getRequestBody().readNBytes(properties.maxBodyBytes() + 1));
            }
        }
    }

    private void serveAssistantMcp(HttpExchange exchange) throws IOException {
        try (exchange) {
            var candidate = exchange.getRequestURI().getPath().substring(ASSISTANT_MCP_CONTEXT_PATH.length());
            if (!MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), assistantSecret)) {
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
            } else if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, NO_RESPONSE_BODY);
            } else {
                respondToMcp(exchange, assistantMcp::handle, exchange.getRequestBody().readNBytes(properties.maxBodyBytes() + 1));
            }
        }
    }

    /** Answers at once with an empty 200, except calls that ask for a decision: those wait for the user (see holdForDecision). */
    private void respondToHook(HttpExchange exchange, String ccmSessionId, byte[] body, BodyConsumer consumer) throws IOException {
        var receivedAt = Instant.now();
        var payload = parseObject(ccmSessionId, body);
        if (payload.filter(this::asksForDecision).isPresent()) {
            respondWithDecision(exchange, holdForDecision(ccmSessionId, payload.get(), receivedAt));
            return;
        }
        exchange.sendResponseHeaders(OK, NO_RESPONSE_BODY);
        payload.ifPresent(parsed -> worker.execute(() -> consume(ccmSessionId, parsed, receivedAt, consumer)));
    }

    private void respondToMcp(HttpExchange exchange, Function<byte[], Optional<String>> handler, byte[] body) throws IOException {
        if (body.length > properties.maxBodyBytes()) {
            exchange.sendResponseHeaders(PAYLOAD_TOO_LARGE, NO_RESPONSE_BODY);
            return;
        }
        var json = handler.apply(body);
        if (json.isEmpty()) {
            exchange.sendResponseHeaders(ACCEPTED, NO_RESPONSE_BODY);
            return;
        }
        writeJson(exchange, json.get());
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(OK, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private boolean asksForDecision(JsonNode payload) {
        return HookEventType.of(payload.path("hook_event_name").asString("")).awaitsDecision();
    }

    /** The event is stored and published on the worker first, so its order and the NEEDS_ACTION status are as for any hook; the worker never waits. */
    private CompletableFuture<Optional<String>> holdForDecision(String ccmSessionId, JsonNode payload, Instant receivedAt) {
        var decision = new CompletableFuture<Optional<String>>();
        worker.execute(() -> {
            try {
                proposals.hold(acceptHook(ccmSessionId, payload, receivedAt)).whenComplete((output, error) -> decision.complete(output == null ? Optional.empty() : output));
            } catch (RuntimeException e) {
                log.debug("Could not hold hook call for session {}: {}", ccmSessionId, e.toString());
                decision.complete(Optional.empty());
            }
        });
        return decision;
    }

    /** Runs on a virtual thread, so waiting for the user costs nothing; ProposalService bounds the wait. */
    private void respondWithDecision(HttpExchange exchange, CompletableFuture<Optional<String>> decision) throws IOException {
        var output = decision.join();
        if (output.isPresent()) {
            writeJson(exchange, output.get());
        } else {
            exchange.sendResponseHeaders(OK, NO_RESPONSE_BODY);
        }
    }

    /** The session id of a request whose path carries the right secret, otherwise empty. */
    private Optional<String> authorizedSessionId(HttpExchange exchange, String contextPath) {
        var parts = exchange.getRequestURI().getPath().substring(contextPath.length()).split("/", -1);
        return parts.length == PATH_PARTS && secretMatches(parts[0]) ? Optional.of(parts[1]) : Optional.empty();
    }

    private Optional<JsonNode> parseObject(String ccmSessionId, byte[] body) {
        if (body.length > properties.maxBodyBytes()) {
            log.debug("Dropped oversized hook body for session {}", ccmSessionId);
            return Optional.empty();
        }
        try {
            JsonNode payload = mapper.readTree(body);
            return payload.isObject() ? Optional.of(payload) : Optional.empty();
        } catch (RuntimeException e) {
            log.debug("Ignored unusable hook call for session {}: {}", ccmSessionId, e.toString());
            return Optional.empty();
        }
    }

    private void consume(String ccmSessionId, JsonNode payload, Instant receivedAt, BodyConsumer consumer) {
        try {
            consumer.accept(ccmSessionId, payload, receivedAt);
        } catch (RuntimeException e) {
            log.debug("Ignored unusable hook call for session {}: {}", ccmSessionId, e.toString());
        }
    }

    private HookEvent acceptHook(String ccmSessionId, JsonNode payload, Instant receivedAt) {
        var rawName = payload.path("hook_event_name").asString("");
        var event = new HookEvent(ccmSessionId, HookEventType.of(rawName), rawName, payload, receivedAt);
        log.debug("Hook event {} for session {}", rawName, ccmSessionId);
        persist(event);
        publisher.publishEvent(event);
        return event;
    }

    private void acceptStatusLine(String ccmSessionId, JsonNode payload, Instant receivedAt) {
        publisher.publishEvent(new StatusLineReport(ccmSessionId, payload, receivedAt));
    }

    private void persist(HookEvent event) {
        try {
            store.save(event);
        } catch (RuntimeException e) {
            log.warn("Could not store hook event {}: {}", event.rawName(), e.toString());
        }
    }

    private boolean secretMatches(String candidate) {
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), secret);
    }

    @FunctionalInterface
    private interface BodyResponder {
        void respond(HttpExchange exchange, String ccmSessionId, byte[] body) throws IOException;
    }

    @FunctionalInterface
    private interface BodyConsumer {
        void accept(String ccmSessionId, JsonNode payload, Instant receivedAt);
    }

    private static byte[] secret() {
        var bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).getBytes(StandardCharsets.US_ASCII);
    }
}
