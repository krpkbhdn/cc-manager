package com.github.krpkbhdn.ccmanager.run;

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeCommand;
import com.github.krpkbhdn.ccmanager.claude.launch.PtySpawner;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;
import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import com.github.krpkbhdn.ccmanager.workspace.WindowsArgument;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs run configurations in a PTY, one run per configuration at a time; the last run stays until replaced or deleted. */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);
    private static final Duration RESTART_EXIT_WAIT = Duration.ofSeconds(5);

    private final RunConfigurationService configurations;
    private final RunShell shell;
    private final HostEnvironment host;
    private final Map<Long, ActiveRun> runs = new ConcurrentHashMap<>();
    private final ChangeListeners listeners = new ChangeListeners("Run");
    private final Map<Long, Object> startLocks = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    public RunService(RunConfigurationService configurations, ProjectService projects, RunShell shell, HostEnvironment host) {
        this.configurations = configurations;
        this.shell = shell;
        this.host = host;
        configurations.subscribe(this::dropRunsOfDeletedConfigurations);
        projects.subscribe(this::dropRunsOfDeletedConfigurations);
    }

    /** Restarts the configuration when it is already running.
     * @throws IllegalStateException with a user-readable message (configuration or directory gone, spawn failed) */
    public LaunchedSession start(long configurationId) {
        synchronized (startLocks.computeIfAbsent(configurationId, id -> new Object())) {
            return startLocked(configurationId);
        }
    }

    private LaunchedSession startLocked(long configurationId) {
        findRunnable(configurationId);
        stopAndAwaitExit(configurationId);
        var configuration = findRunnable(configurationId);
        var runId = "run-" + configurationId + "-" + counter.incrementAndGet();
        PtyProcess process;
        try {
            process = spawn(configuration);
        } catch (IOException | RuntimeException e) {
            log.warn("Run configuration {} failed to start", configurationId, e);
            throw new IllegalStateException("Could not start \"" + configuration.name() + "\": " + e.getMessage(), e);
        }
        runs.put(configurationId, new ActiveRun(configurationId, runId, configuration.name(), process, OptionalInt.empty()));
        watchExit(configurationId, runId, process);
        listeners.fire();
        return new LaunchedSession(runId, configuration.projectId(), configuration.name(), process);
    }

    private RunConfiguration findRunnable(long configurationId) {
        var configuration = configurations.find(configurationId)
                .orElseThrow(() -> new IllegalStateException("The run configuration no longer exists."));
        if (!configuration.confirmed()) {
            throw new IllegalStateException("The command of \"" + configuration.name() + "\" was written by an agent and needs the user's confirmation first.");
        }
        if (!Files.isDirectory(configuration.directory())) {
            throw new IllegalStateException("The run directory is missing: " + configuration.directory());
        }
        return configuration;
    }

    /** Kills the process tree of a running configuration; the exit is reported by the watcher. */
    public void stop(long configurationId) {
        var run = runs.get(configurationId);
        if (run != null && run.running()) {
            ProcessTree.kill(run.process());
        }
    }

    private void stopAndAwaitExit(long configurationId) {
        var previous = runs.get(configurationId);
        if (previous == null) {
            return;
        }
        if (!previous.running()) {
            return;
        }
        var exits = new ArrayList<CompletableFuture<?>>();
        exits.add(previous.process().onExit());
        ProcessHandle.of(previous.process().pid()).ifPresent(handle -> handle.descendants().forEach(child -> exits.add(child.onExit())));
        stop(configurationId);
        try {
            CompletableFuture.allOf(exits.toArray(CompletableFuture[]::new)).get(RESTART_EXIT_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Previous run of configuration {} did not exit in time", configurationId);
        }
    }

    public Optional<ActiveRun> current(long configurationId) {
        return Optional.ofNullable(runs.get(configurationId));
    }

    /** @return unsubscribe handle; callback fires (any thread) when a run starts, exits or is stopped */
    public Runnable subscribe(Runnable onChange) {
        return listeners.add(onChange);
    }

    @PreDestroy
    void stopAll() {
        runs.keySet().forEach(this::stop);
    }

    private PtyProcess spawn(RunConfiguration configuration) throws IOException {
        return new PtyProcessBuilder(arguments(configuration.command()))
                .setDirectory(configuration.directory().toString())
                .setUseWinConPty(true)
                .setEnvironment(ClaudeCommand.terminalEnvironment(System.getenv()))
                .setInitialColumns(PtySpawner.INITIAL_COLUMNS)
                .setInitialRows(PtySpawner.INITIAL_ROWS)
                .start();
    }

    private String[] arguments(String command) {
        return quotedForWindows(shell.invocation(command), host.windows());
    }

    /** cmd.exe does not understand backslash-escaped quotes, so its arguments go through as is (RunShell already wraps the command for {@code /s /c}). */
    static String[] quotedForWindows(List<String> invocation, boolean windows) {
        var verbatim = !windows || RunShell.isCmd(invocation.getFirst());
        return invocation.stream().map(argument -> verbatim ? argument : WindowsArgument.quote(argument)).toArray(String[]::new);
    }

    private void watchExit(long configurationId, String runId, PtyProcess process) {
        var watcher = new Thread(() -> awaitExit(configurationId, runId, process), "run-exit-" + runId);
        watcher.setDaemon(true);
        watcher.start();
    }

    private void awaitExit(long configurationId, String runId, PtyProcess process) {
        try {
            int code = process.waitFor();
            runs.computeIfPresent(configurationId, (id, run) -> run.runId().equals(runId) ? run.exited(code) : run);
            listeners.fire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dropRunsOfDeletedConfigurations() {
        var existing = configurations.existingIds();
        for (var configurationId : runs.keySet()) {
            if (!existing.contains(configurationId)) {
                stop(configurationId);
                runs.remove(configurationId);
                listeners.fire();
            }
        }
        // A deleted configuration cannot be started again (ids are not reused), so its lock can go without a race.
        startLocks.keySet().retainAll(existing);
    }
}
