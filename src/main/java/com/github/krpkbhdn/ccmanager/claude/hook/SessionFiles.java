package com.github.krpkbhdn.ccmanager.claude.hook;

import java.nio.file.Path;

/** The per-session files handed to claude: --settings and --mcp-config. */
public record SessionFiles(Path settings, Path mcpConfig) {}
