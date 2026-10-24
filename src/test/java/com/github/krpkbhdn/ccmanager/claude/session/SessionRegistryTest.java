package com.github.krpkbhdn.ccmanager.claude.session;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {

    private static final int THREADS = 8;
    private static final int SESSIONS_PER_THREAD = 50;

    private final SessionRegistry registry = new SessionRegistry();

    private HookEvent eventFor(String ccmId, String name, String json) {
        var template = of(name, json);
        return new HookEvent(ccmId, template.type(), name, template.payload(), template.receivedAt());
    }

    @Test
    void registeredSessionStartsIdleAndEndedSessionLeaves() {
        var id = registry.register(1);

        assertThat(registry.find(id)).get().extracting(SessionSnapshot::status).isEqualTo(AgentStatus.IDLE);

        registry.markEnded(id, 0);

        assertThat(registry.find(id)).isEmpty();
        assertThat(registry.sessions()).isEmpty();
    }

    @Test
    void hookEventsDriveStatusAndProjectStatusIsMaxOverSessions() {
        var first = registry.register(1);
        registry.register(1);
        registry.register(2);
        assertThat(registry.projectStatus(3)).isEqualTo(AgentStatus.OFFLINE);

        registry.on(eventFor(first, "UserPromptSubmit", "{}"));
        assertThat(registry.projectStatus(1)).isEqualTo(AgentStatus.WORKING);
        assertThat(registry.projectStatus(2)).isEqualTo(AgentStatus.IDLE);

        registry.on(eventFor(first, "PermissionRequest", "{\"tool_name\":\"Bash\"}"));
        assertThat(registry.projectStatus(1)).isEqualTo(AgentStatus.NEEDS_ACTION);
        assertThat(registry.sessions(1)).hasSize(2);
    }

    @Test
    void eventsForUnknownSessionAreIgnored() {
        registry.on(eventFor("ghost", "UserPromptSubmit", "{}"));

        assertThat(registry.sessions()).isEmpty();
    }

    @Test
    void interruptTurnsWorkingIntoIdle() {
        var id = registry.register(1);
        registry.on(eventFor(id, "UserPromptSubmit", "{}"));

        registry.markInterrupted(id);

        assertThat(registry.find(id)).get().extracting(SessionSnapshot::status).isEqualTo(AgentStatus.IDLE);
    }

    @Test
    void listenersAreNotifiedAndFailingOneDoesNotBreakOthers() {
        var calls = new AtomicInteger();
        registry.subscribe(() -> { throw new IllegalStateException("boom"); });
        var unsubscribe = registry.subscribe(calls::incrementAndGet);

        var id = registry.register(1);
        registry.on(eventFor(id, "Stop", "{}"));
        unsubscribe.run();
        registry.markEnded(id, 0);

        assertThat(calls).hasValue(2);
    }

    @Test
    void concurrentRegistrationsAndEventsStayConsistent() throws Exception {
        var ids = new ArrayList<String>();
        var done = new CountDownLatch(THREADS);
        try (var pool = Executors.newFixedThreadPool(THREADS)) {
            for (var t = 0; t < THREADS; t++) {
                pool.execute(() -> {
                    var mine = new ArrayList<String>();
                    for (var i = 0; i < SESSIONS_PER_THREAD; i++) {
                        var id = registry.register(1);
                        registry.on(eventFor(id, "UserPromptSubmit", "{}"));
                        mine.add(id);
                    }
                    synchronized (ids) {
                        ids.addAll(mine);
                    }
                    done.countDown();
                });
            }
            done.await();
        }

        assertThat(ids).hasSize(THREADS * SESSIONS_PER_THREAD).doesNotHaveDuplicates();
        List<SessionSnapshot> sessions = registry.sessions(1);
        assertThat(sessions).hasSize(ids.size()).allMatch(session -> session.status() == AgentStatus.WORKING);
    }
}
