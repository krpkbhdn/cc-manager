package com.github.krpkbhdn.ccmanager.run;

import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeCommand;
import com.github.krpkbhdn.ccmanager.claude.launch.PtySpawner;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;
import com.github.krpkbhdn.ccmanager.claude.launch.WindowsArgument;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs run configurations in a PTY, one run per configuration at a time; the last run stays until replaced or deleted. */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final RunConfigurationService configurations;
    private final RunShell shell;
    private final HostEnvironment host;
    private final Map<Long, ActiveRun> runs = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong counter = new AtomicLong();

    public RunService(RunConfigurationService configurations, RunShell shell, HostEnvironment host) {
        this.configurations = configurations;
        this.shell = shell;
        this.host = host;
        configurations.subscribe(this::dropRunsOfDeletedConfigurations);
    }

    /** Restarts the configuration when it is already running.
     * @throws IllegalStateException with a user-readable message (configuration or directory gone, spawn failed) */
    public synchronized LaunchedSession start(long configurationId) {
        var configuration = configurations.find(configurationId)
                .orElseThrow(() -> new IllegalStateException("The run configuration no longer exists."));
        if (!Files.isDirectory(configuration.directory())) {
            throw new IllegalStateException("The run directory is missing: " + configuration.directory());
        }
        stop(configurationId);
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
        fireChanged();
        return new LaunchedSession(runId, configuration.projectId(), configuration.name(), process);
    }

    /** Kills the process tree of a running configuration; the exit is reported by the watcher. */
    public void stop(long configurationId) {
        var run = runs.get(configurationId);
        if (run != null && run.running()) {
            ProcessTree.kill(run.process());
        }
    }

    public Optional<ActiveRun> current(long configurationId) {
        return Optional.ofNullable(runs.get(configurationId));
    }

    /** @return unsubscribe handle; callback fires (any thread) when a run starts, exits or is stopped */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
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
        var invocation = shell.invocation(command);
        var windowsQuoting = host.windows();
        return invocation.stream().map(argument -> windowsQuoting ? WindowsArgument.quote(argument) : argument).toArray(String[]::new);
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
            fireChanged();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dropRunsOfDeletedConfigurations() {
        for (var configurationId : runs.keySet()) {
            if (configurations.find(configurationId).isEmpty()) {
                stop(configurationId);
                runs.remove(configurationId);
                fireChanged();
            }
        }
    }

    private void fireChanged() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("Run listener failed", e);
            }
        }
    }
}
