package com.github.krpkbhdn.ccmanager.claude.launch;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Starts a command in a pty4j PTY, quoting the arguments on Windows where pty4j does not. */
public final class PtySpawner {

    public static final int INITIAL_COLUMNS = 120;
    public static final int INITIAL_ROWS = 32;

    private PtySpawner() {}

    public static PtyProcess spawn(List<String> command, Path directory, Map<String, String> environment) throws IOException {
        return new PtyProcessBuilder(platformArguments(command))
                .setDirectory(directory.toString())
                .setUseWinConPty(true)
                .setEnvironment(environment)
                .setInitialColumns(INITIAL_COLUMNS)
                .setInitialRows(INITIAL_ROWS)
                .start();
    }

    private static String[] platformArguments(List<String> command) {
        var windows = System.getProperty("os.name").startsWith("Windows");
        if (!windows) {
            return command.toArray(String[]::new);
        }
        WindowsArgument.requireSafeForBatchFile(command);
        var quoted = command.stream().map(WindowsArgument::quote).toArray(String[]::new);
        WindowsArgument.requireFitsCommandLine(quoted);
        return quoted;
    }
}
