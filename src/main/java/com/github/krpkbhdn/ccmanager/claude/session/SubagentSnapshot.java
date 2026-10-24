package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Instant;

public record SubagentSnapshot(String agentId, String agentType, String activity, boolean running, Instant startedAt) {}
