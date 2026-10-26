package com.github.krpkbhdn.ccmanager.run;

import com.pty4j.PtyProcess;
import java.util.OptionalInt;

/** The latest run of a configuration; kept after the process exits so its console and exit code stay visible. */
public record ActiveRun(long configurationId, String runId, String label, PtyProcess process, OptionalInt exitCode) {

    public boolean running() {
        return exitCode.isEmpty();
    }

    ActiveRun exited(int code) {
        return new ActiveRun(configurationId, runId, label, process, OptionalInt.of(code));
    }
}
