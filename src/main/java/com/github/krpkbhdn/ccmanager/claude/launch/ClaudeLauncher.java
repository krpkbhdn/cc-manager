package com.github.krpkbhdn.ccmanager.claude.launch;

import org.springframework.stereotype.Service;

/**
 * Starts the native claude in a PTY inside the project directory with hooks injected via --settings, registers the
 * session and reports process exit to the registry. The UI attaches a terminal widget to the returned process.
 */
@Service
public class ClaudeLauncher {

    /** @throws IllegalStateException with a user-readable message (Claude missing, project gone, spawn failed) */
    public LaunchedSession launch(long projectId, LaunchMode mode) { throw todo(); }

    /** claude attach ID for a background agent. */
    public LaunchedSession attach(long projectId, String backgroundSessionId) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
