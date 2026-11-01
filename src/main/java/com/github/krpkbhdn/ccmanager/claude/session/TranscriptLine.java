package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Instant;
import java.util.List;

/** One parsed transcript JSONL line; {@code at} is {@link Instant#EPOCH} and the text fields are empty when the line lacks them. */
public record TranscriptLine(String uuid, String sessionId, String cwd, Instant at, List<TranscriptEntry> entries) {}
