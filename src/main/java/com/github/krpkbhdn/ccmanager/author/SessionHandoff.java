package com.github.krpkbhdn.ccmanager.author;

import java.nio.file.Path;

/** What the launcher needs to start the Claude session that finishes the setup. */
public record SessionHandoff(String firstMessage, Path profileDirectory) {}
