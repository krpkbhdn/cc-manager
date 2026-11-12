package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.mcp.AssistantMcpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String ASSISTANT_MCP_PATH = "/assistant-mcp";
    /** Env variable of the Assistant process that carries the bearer token its mcp file refers to. */
    public static final String ASSISTANT_TOKEN_ENV = "CCM_ASSISTANT_TOKEN";
    /** Env variable of a session's claude process that carries its bearer token; the settings and mcp files refer to it. */
    public static final String SESSION_TOKEN_ENV = "CCM_HOOK_TOKEN";
    private static final int OK = 200;
    private static final int ACCEPTED = 202;
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int NO_RESPONSE_BODY = -1;
    private static final int PAYLOAD_TOO_LARGE = 413;
    private static final java.util.Set<HookEventType> STATUS_ENDING_EVENTS = java.util.EnumSet.of(HookEventType.STOP,
            HookEventType.POST_TOOL_USE, HookEventType.POST_TOOL_USE_FAILURE, HookEventType.USER_PROMPT_SUBMIT,
            HookEventType.ELICITATION_RESULT, HookEventType.SESSION_END, HookEventType.STOP_FAILURE, HookEventType.NOTIFICATION,
            HookEventType.SUBAGENT_STOP, HookEventType.POST_COMPACT);

    private final HookEventStore store;
    private final ApplicationEventPublisher publisher;
    private final HookProperties properties;
    private final McpEndpoint mcp;
    private final AssistantMcpServer assistantMcp;
    private final DecisionHolder proposals;
    private final DecisionHold decisionHold;
    private final HookPayloadParser parser;
    private final HookCredentials credentials = new HookCredentials();
    private final AtomicLong droppedCalls = new AtomicLong();
    private volatile OrderedWorker worker;
    private volatile OrderedWorker persister;
    private HttpServer server;
    private ExecutorService requestExecutor;

    public HookServer(HookEventStore store, ApplicationEventPublisher publisher, JsonMapper mapper, HookProperties properties,
            McpEndpoint mcp, AssistantMcpServer assistantMcp, DecisionHolder proposals) {
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
        this.mcp = mcp;
        this.assistantMcp = assistantMcp;
        this.proposals = proposals;
        this.decisionHold = new DecisionHold(proposals, properties);
        this.parser = new HookPayloadParser(mapper, properties);
    }

    public synchronized int port() {
        if (server == null) {
            throw new IllegalStateException("Hook server is not running");
        }
        return server.getAddress().getPort();
    }

    /** URL a hook of the given ccm session posts to; it holds no secret, the credential is {@link #sessionToken(String)} sent as a bearer token. */
    public String hookUrl(String ccmSessionId) {
        return "http://127.0.0.1:" + port() + CONTEXT_PATH + ccmSessionId;
    }

    /** The session's credential; handed to its claude process through {@link #SESSION_TOKEN_ENV} only, never written to disk. */
    public String sessionToken(String ccmSessionId) {
        return credentials.sessionToken(ccmSessionId);
    }

    /** URL of the MCP server a session talks to; same bearer token, own path because it answers instead of just accepting. */
    public String mcpUrl(String ccmSessionId) {
        return hookUrl(ccmSessionId).replace(CONTEXT_PATH, MCP_CONTEXT_PATH);
    }

    /** URL of the app MCP server of the Assistant session; carries no secret, the bearer token is {@link #assistantMcpToken()}. */
    public String assistantMcpUrl() {
        return "http://127.0.0.1:" + port() + ASSISTANT_MCP_PATH;
    }

    /** The Assistant's own secret; handed to the Assistant process through its environment only, never written to disk. */
    public String assistantMcpToken() {
        return credentials.assistantToken();
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
        server.createContext(ASSISTANT_MCP_PATH, this::serveAssistantMcp);
        worker = new OrderedWorker("hook-worker", properties.maxQueuedCalls());
        persister = new OrderedWorker("hook-persister", properties.maxQueuedCalls());
        requestExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(requestExecutor);
        server.start();
        log.info("Hook server listening on 127.0.0.1:{}", server.getAddress().getPort());
    }

    @Override
    public void stop() {
        HttpServer stopping;
        ExecutorService requests;
        OrderedWorker work;
        OrderedWorker persist;
        synchronized (this) {
            stopping = server;
            requests = requestExecutor;
            work = worker;
            persist = persister;
            server = null;
        }
        if (stopping != null) {
            stopping.stop(0);
            proposals.releaseAll();
            // bounded drain: each queue gets a short wait (OrderedWorker) to flush, then shutdownNow drops the rest
            OrderedWorker.awaitTermination(requests);
            work.awaitTermination();
            persist.awaitTermination();
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return server != null;
    }

    private void serve(HttpExchange exchange, String contextPath, BodyResponder responder) throws IOException {
        try (exchange) {
            var ccmSessionId = credentials.authorizedSessionId(exchange, contextPath);
            log.debug("{} {}{} accept={} mcp-session-id={}", exchange.getRequestMethod(), contextPath,
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
            if (!ASSISTANT_MCP_PATH.equals(exchange.getRequestURI().getPath()) || !credentials.isAssistant(exchange)) {
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
            } else if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, NO_RESPONSE_BODY);
            } else {
                respondToMcp(exchange, assistantMcp::handle, exchange.getRequestBody().readNBytes(properties.maxBodyBytes() + 1));
            }
        }
    }

    /** Answers at once with an empty 200, except calls that ask for a decision: those wait for the user (see DecisionHold). */
    private void respondToHook(HttpExchange exchange, String ccmSessionId, byte[] body, BodyConsumer consumer) throws IOException {
        var receivedAt = Instant.now();
        var payload = parser.parseObject(ccmSessionId, body);
        if (body.length <= properties.maxBodyBytes() && payload.filter(this::asksForDecision).isPresent()) {
            var decision = decisionHold.hold(worker, ccmSessionId, () -> acceptHook(ccmSessionId, payload.get(), receivedAt),
                    () -> dropped("held hook call", ccmSessionId));
            respondWithDecision(exchange, decision);
            return;
        }
        exchange.sendResponseHeaders(OK, NO_RESPONSE_BODY);
        try {
            payload.ifPresent(parsed -> worker.execute(() -> consume(ccmSessionId, parsed, receivedAt, consumer)));
        } catch (RejectedExecutionException e) {
            if (payload.filter(this::endsAStatus).isPresent()) {
                exchange.close();
                enqueueWhenRoomFrees(ccmSessionId, payload.get(), receivedAt, consumer);
            } else {
                dropped("hook call", ccmSessionId);
            }
        }
    }

    private void enqueueWhenRoomFrees(String ccmSessionId, JsonNode payload, Instant receivedAt, BodyConsumer consumer) {
        if (!worker.executeWhenRoomFrees(() -> consume(ccmSessionId, payload, receivedAt, consumer))) {
            dropped("hook call", ccmSessionId);
        }
    }

    /** Losing these leaves a session status or a held dialog wrong until the next event, so a full queue does not drop them. */
    private boolean endsAStatus(JsonNode payload) {
        return STATUS_ENDING_EVENTS.contains(HookEventType.of(payload.path("hook_event_name").asString("")));
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

    /** Runs on a virtual thread, so waiting for the user costs nothing; the wait is bounded by the decision hold. */
    private void respondWithDecision(HttpExchange exchange, CompletableFuture<Optional<String>> decision) throws IOException {
        var output = decision.join();
        if (output.isPresent()) {
            writeJson(exchange, output.get());
        } else {
            exchange.sendResponseHeaders(OK, NO_RESPONSE_BODY);
        }
    }

    private void dropped(String what, String id) {
        log.warn("Dropped {} {}: queue is full or the server is stopping ({} dropped so far)", what, id, droppedCalls.incrementAndGet());
    }

    private void consume(String ccmSessionId, JsonNode payload, Instant receivedAt, BodyConsumer consumer) {
        try {
            consumer.accept(ccmSessionId, payload, receivedAt);
        } catch (RuntimeException e) {
            log.warn("Failed to handle hook call for session {}: {}", ccmSessionId, e.toString());
        }
    }

    private HookEvent acceptHook(String ccmSessionId, JsonNode payload, Instant receivedAt) {
        var rawName = payload.path("hook_event_name").asString("");
        var event = new HookEvent(ccmSessionId, HookEventType.of(rawName), rawName, payload, receivedAt);
        log.debug("Hook event {} for session {}", rawName, ccmSessionId);
        publisher.publishEvent(event);
        persistLater(event);
        return event;
    }

    /** Stored after publishing, on its own thread, so a locked database never delays statuses or proposal dialogs. */
    private void persistLater(HookEvent event) {
        try {
            persister.execute(() -> persist(event));
        } catch (RejectedExecutionException e) {
            dropped("event write", event.rawName());
        }
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

    @FunctionalInterface
    private interface BodyResponder {
        void respond(HttpExchange exchange, String ccmSessionId, byte[] body) throws IOException;
    }

    @FunctionalInterface
    private interface BodyConsumer {
        void accept(String ccmSessionId, JsonNode payload, Instant receivedAt);
    }
}
