package com.github.krpkbhdn.ccmanager.claude.install;

import java.nio.file.Path;

/** Result of looking for the native Claude Code CLI. problem is a user-readable reason when not installed. */
public record ClaudeInstallation(boolean installed, Path executable, String version, LoginState login, String problem) {}
