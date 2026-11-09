package com.github.krpkbhdn.ccmanager.run;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.project.GroupWorkspace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.workspace.SystemHostEnvironment;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Runs real shell commands in a PTY; the long-running one works under bash (sleep) and cmd (ping). */
class RunServiceTest {

    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final String LONG_COMMAND = "sleep 60 || ping -n 60 127.0.0.1";

    @TempDir
    Path temp;

    RunConfigurationService configurations;
    ProjectService projects;
    RunService runs;
    long projectId;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        configurations = new RunConfigurationService(jdbc);
        projects = new ProjectService(jdbc, new GroupWorkspace(new AppProperties(temp)));
        var host = new SystemHostEnvironment();
        runs = new RunService(configurations, projects, new RunShell(host), host);
    }

    @AfterEach
    void tearDown() {
        runs.stopAll();
    }

    @Test
    void reportsTheExitCodeAndKeepsTheFinishedRun() throws Exception {
        var configuration = configurations.save(projectId, "fail", "exit 3", temp);
        var exited = new CountDownLatch(1);
        runs.subscribe(() -> runs.current(configuration.id()).filter(run -> !run.running()).ifPresent(run -> exited.countDown()));

        var launched = runs.start(configuration.id());

        assertThat(exited.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        var run = runs.current(configuration.id()).orElseThrow();
        assertThat(run.runId()).isEqualTo(launched.ccmSessionId()).startsWith("run-" + configuration.id() + "-");
        assertThat(run.exitCode()).hasValue(3);
    }

    @Test
    void refusesToStartAnUnconfirmedAgentConfiguration() {
        var configuration = configurations.saveByAgent(projectId, "agent", "exit 0", temp);

        assertThatThrownBy(() -> runs.start(configuration.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("confirmation");
    }

    @Test
    void restartingUsesANewRunId() {
        var configuration = configurations.save(projectId, "again", "exit 0", temp);

        var first = runs.start(configuration.id());
        var second = runs.start(configuration.id());

        assertThat(second.ccmSessionId()).isNotEqualTo(first.ccmSessionId());
    }

    @Test
    void stopKillsALongRunningCommand() throws Exception {
        var configuration = configurations.save(projectId, "long", LONG_COMMAND, temp);

        var launched = runs.start(configuration.id());
        assertThat(runs.current(configuration.id()).orElseThrow().running()).isTrue();
        runs.stop(configuration.id());

        assertThat(launched.process().waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void deletingARunningConfigurationStopsAndForgetsIt() throws Exception {
        var configuration = configurations.save(projectId, "long", LONG_COMMAND, temp);
        var launched = runs.start(configuration.id());

        configurations.delete(configuration.id());

        assertThat(runs.current(configuration.id())).isEmpty();
        assertThat(launched.process().waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void removingTheProjectStopsAndForgetsItsRuns() throws Exception {
        var configuration = configurations.save(projectId, "long", LONG_COMMAND, temp);
        var launched = runs.start(configuration.id());

        projects.remove(projectId);

        assertThat(runs.current(configuration.id())).isEmpty();
        assertThat(launched.process().waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cmdReceivesTheCommandVerbatimWhileOtherWindowsShellsGetQuotedArguments() {
        var command = "npm run x -- --name \"a b\"";

        assertThat(RunService.quotedForWindows(List.of("cmd.exe", "/c", command), true)).containsExactly("cmd.exe", "/c", command);
        assertThat(RunService.quotedForWindows(List.of("bash.exe", "-c", command), true))
                .containsExactly("bash.exe", "-c", "\"npm run x -- --name \\\"a b\\\"\"");
        assertThat(RunService.quotedForWindows(List.of("/bin/sh", "-lc", command), false)).containsExactly("/bin/sh", "-lc", command);
    }

    @Test
    void restartDoesNotSpawnWhenTheConfigurationWasDeletedDuringTheWait() {
        var configuration = configurations.save(projectId, "gone", "exit 0", temp);
        var spied = org.mockito.Mockito.spy(configurations);
        org.mockito.Mockito.doCallRealMethod().doReturn(java.util.Optional.empty()).when(spied).find(configuration.id());
        var host = new SystemHostEnvironment();
        var service = new RunService(spied, projects, new RunShell(host), host);

        assertThatThrownBy(() -> service.start(configuration.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer exists");
        assertThat(service.current(configuration.id())).isEmpty();
    }

    @Test
    void restartDoesNotSpawnACommandAnAgentRewroteDuringTheWait() {
        var configuration = configurations.save(projectId, "rewritten", "exit 0", temp);
        var spied = org.mockito.Mockito.spy(configurations);
        org.mockito.Mockito.doCallRealMethod()
                .doAnswer(invocation -> {
                    configurations.saveByAgent(projectId, "rewritten", "exit 1", temp);
                    return invocation.callRealMethod();
                })
                .when(spied).find(configuration.id());
        var host = new SystemHostEnvironment();
        var service = new RunService(spied, projects, new RunShell(host), host);

        assertThatThrownBy(() -> service.start(configuration.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("confirmation");
        assertThat(service.current(configuration.id())).isEmpty();
    }

    @Test
    void startOfAnotherConfigurationIsNotBlockedByASlowStart() throws Exception {
        var slow = configurations.save(projectId, "slow", "exit 0", temp);
        var fast = configurations.save(projectId, "fast", "exit 0", temp);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = serviceWhoseLookupOf(slow.id(), entered, release);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var slowStart = pool.submit(() -> service.start(slow.id()));
            assertThat(entered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

            var fastStart = pool.submit(() -> service.start(fast.id()));

            assertThat(fastStart.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).ccmSessionId()).startsWith("run-" + fast.id() + "-");
            release.countDown();
            slowStart.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
            service.stopAll();
        }
    }

    @Test
    void secondConcurrentStartWaitsForTheFirstRunToExitBeforeSpawning() throws Exception {
        var configuration = configurations.save(projectId, "long", LONG_COMMAND, temp);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = serviceWhoseLookupOf(configuration.id(), entered, release);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.start(configuration.id()));
            assertThat(entered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> service.start(configuration.id()));
            release.countDown();

            var firstRun = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            var secondRun = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(firstRun.process().isAlive()).isFalse();
            assertThat(secondRun.process().isAlive()).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
            service.stopAll();
        }
    }

    @Test
    void concurrentStartsOfTheSameConfigurationAreSerialised() throws Exception {
        var configuration = configurations.save(projectId, "same", "exit 0", temp);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var service = serviceWhoseLookupOf(configuration.id(), entered, release);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.start(configuration.id()));
            assertThat(entered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

            var second = pool.submit(() -> service.start(configuration.id()));

            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            var firstId = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).ccmSessionId();
            assertThat(second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).ccmSessionId()).isNotEqualTo(firstId);
        } finally {
            release.countDown();
            pool.shutdownNow();
            service.stopAll();
        }
    }

    /** A service whose second lookup (the one inside the start lock) waits for {@code release}, so a start can be held inside its lock. */
    private RunService serviceWhoseLookupOf(long configurationId, CountDownLatch entered, CountDownLatch release) {
        var spied = org.mockito.Mockito.spy(configurations);
        var lookups = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (lookups.incrementAndGet() == 2) {
                entered.countDown();
                release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(spied).find(configurationId);
        var host = new SystemHostEnvironment();
        return new RunService(spied, projects, new RunShell(host), host);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletingAConfigurationDropsItsStartLock() throws Exception {
        var configuration = configurations.save(projectId, "short", "exit 0", temp);
        runs.start(configuration.id());
        var locks = RunService.class.getDeclaredField("startLocks");
        locks.setAccessible(true);
        var startLocks = (java.util.Map<Long, Object>) locks.get(runs);
        assertThat(startLocks).containsKey(configuration.id());

        configurations.delete(configuration.id());

        assertThat(startLocks).doesNotContainKey(configuration.id());
    }

    @Test
    void startFailsReadablyForAMissingConfiguration() {
        assertThatThrownBy(() -> runs.start(12345)).isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer exists");
    }

    @Test
    void throwingSubscriberDoesNotStopTheOthers() throws Exception {
        var configuration = configurations.save(projectId, "ok", "exit 0", temp);
        var notified = new CountDownLatch(1);
        runs.subscribe(() -> { throw new IllegalStateException("boom"); });
        runs.subscribe(notified::countDown);

        runs.start(configuration.id());

        assertThat(notified.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }
}
