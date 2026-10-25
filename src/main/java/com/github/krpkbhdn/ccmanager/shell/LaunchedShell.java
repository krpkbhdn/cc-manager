package com.github.krpkbhdn.ccmanager.shell;

import com.pty4j.PtyProcess;

/** A running shell; its terminalId never equals a Claude session id, so the session registry ignores it. */
public record LaunchedShell(String terminalId, long projectId, ShellProfile shell, PtyProcess process) {}
