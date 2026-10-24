package com.github.krpkbhdn.ccmanager.claude.hook;

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
 * Always answers 200 with an empty body: anything else would change Claude's behaviour.
 */
@Component
public class HookServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(HookServer.class);
    private static final String CONTEXT_PATH = "/hook/";
    private static final int SECRET_BYTES = 32;
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int NO_RESPONSE_BODY = -1;
    private static final int PATH_PARTS = 2;

    private final HookEventStore store;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper mapper;
    private final HookProperties properties;
    private final byte[] secret = secret();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("hook-worker").daemon().factory());
    private HttpServer server;

    public HookServer(HookEventStore store, ApplicationEventPublisher publisher, JsonMapper mapper, HookProperties properties) {
        this.store = store;
        this.publisher = publisher;
        this.mapper = mapper;
        this.properties = properties;
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
        server.createContext(CONTEXT_PATH, this::handle);
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

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var parts = exchange.getRequestURI().getPath().substring(CONTEXT_PATH.length()).split("/", -1);
            if (parts.length != PATH_PARTS || !secretMatches(parts[0])) {
                exchange.sendResponseHeaders(NOT_FOUND, NO_RESPONSE_BODY);
            } else if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, NO_RESPONSE_BODY);
            } else {
                var body = exchange.getRequestBody().readNBytes(properties.maxBodyBytes() + 1);
                exchange.sendResponseHeaders(OK, NO_RESPONSE_BODY);
                accept(parts[1], body);
            }
        }
    }

    private void accept(String ccmSessionId, byte[] body) {
        if (body.length > properties.maxBodyBytes()) {
            log.debug("Dropped oversized hook body for session {}", ccmSessionId);
            return;
        }
        var receivedAt = Instant.now();
        worker.execute(() -> process(ccmSessionId, body, receivedAt));
    }

    private void process(String ccmSessionId, byte[] body, Instant receivedAt) {
        try {
            JsonNode payload = mapper.readTree(body);
            if (!payload.isObject()) {
                return;
            }
            var rawName = payload.path("hook_event_name").asString("");
            var event = new HookEvent(ccmSessionId, HookEventType.of(rawName), rawName, payload, receivedAt);
            log.debug("Hook event {} for session {}", rawName, ccmSessionId);
            persist(event);
            publisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.debug("Ignored unusable hook call for session {}: {}", ccmSessionId, e.toString());
        }
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

    private static byte[] secret() {
        var bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).getBytes(StandardCharsets.US_ASCII);
    }
}
