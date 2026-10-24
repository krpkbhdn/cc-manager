package com.github.krpkbhdn.ccmanager.claude.launch;

import com.pty4j.PtyProcess;

public record LaunchedSession(String ccmSessionId, long projectId, String label, PtyProcess process) {}
