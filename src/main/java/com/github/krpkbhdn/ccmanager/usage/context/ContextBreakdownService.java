package com.github.krpkbhdn.ccmanager.usage.context;

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeCommand;
import com.github.krpkbhdn.ccmanager.extension.ClaudeCli;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Measures what fills a session's context by running {@code claude -p /context}, which costs no model call (output without a
 * breakdown is a failure, never retried). Concurrent calls for one key share one process. A session id
 * resumes a fork of that session (nothing is persisted, the original transcript stays untouched); without one the result
 * is a fresh-process estimate from the folder's configuration. Runs plain, without hook settings, so it never registers as a session.
 */
@Service
@EnableConfigurationProperties(ContextBreakdownProperties.class)
public class ContextBreakdownService {

    private static final Logger log = LoggerFactory.getLogger(ContextBreakdownService.class);
    private static final String CONTEXT_COMMAND = "/context";

    private final ClaudeCli claude;
    private final ContextBreakdownProperties properties;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ContextBreakdownResult>> inFlight = new ConcurrentHashMap<>();

    @Autowired
    public ContextBreakdownService(ClaudeCli claude, ContextBreakdownProperties properties) {
        this(claude, properties, Clock.systemUTC());
    }

    public ContextBreakdownService(ClaudeCli claude, ContextBreakdownProperties properties, Clock clock) {
        this.claude = claude;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Blocking, takes seconds: call off the UI thread. Never throws.
     *
     * @param directory the session's working folder (a worktree folder for isolated tasks)
     * @param claudeSessionId the session to resume, null for a fresh-process estimate
     * @param force skip the cached result of the last {@code ttl}
     */
    public ContextBreakdownResult measure(Path directory, String claudeSessionId, boolean force) {
        if (claudeSessionId != null) {
            try {
                ClaudeCommand.canonicalUuid(claudeSessionId);
            } catch (IllegalArgumentException e) {
                return ContextBreakdownResult.failed(e.getMessage());
            }
        }
        var key = claudeSessionId != null ? claudeSessionId : directory.toString();
        if (!force) {
            var cached = cache.get(key);
            if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
                return cached.result();
            }
        }
        var mine = new CompletableFuture<ContextBreakdownResult>();
        var running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return running.join();
        }
        try {
            var result = run(directory, claudeSessionId);
            store(key, result);
            mine.complete(result);
            return result;
        } finally {
            inFlight.remove(key);
            mine.complete(ContextBreakdownResult.failed("context measurement aborted"));
        }
    }

    static List<String> arguments(String claudeSessionId) {
        var arguments = new ArrayList<>(List.of("-p"));
        if (claudeSessionId != null) {
            arguments.addAll(List.of("--resume", claudeSessionId, "--fork-session", "--no-session-persistence"));
        }
        arguments.add(CONTEXT_COMMAND);
        return arguments;
    }

    private void store(String key, ContextBreakdownResult result) {
        var now = clock.instant();
        cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        var lifetime = result.succeeded() ? properties.ttl() : properties.failureTtl();
        cache.put(key, new Entry(result, now.plus(lifetime)));
    }

    private ContextBreakdownResult run(Path directory, String claudeSessionId) {
        try {
            var result = claude.execute(directory, properties.timeout(), arguments(claudeSessionId));
            if (!result.succeeded()) {
                return ContextBreakdownResult.failed("claude " + CONTEXT_COMMAND + " exited with code " + result.exitCode());
            }
            return ContextBreakdownParser.parse(result.stdout(), clock.instant())
                    .map(breakdown -> breakdown.withApproximateMessages(claudeSessionId == null))
                    .map(ContextBreakdownResult::success)
                    .orElseGet(() -> ContextBreakdownResult.failed("claude " + CONTEXT_COMMAND + " printed no context breakdown"));
        } catch (RuntimeException e) {
            log.warn("Context breakdown failed", e);
            return ContextBreakdownResult.failed(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private record Entry(ContextBreakdownResult result, Instant expiresAt) {
    }
}
