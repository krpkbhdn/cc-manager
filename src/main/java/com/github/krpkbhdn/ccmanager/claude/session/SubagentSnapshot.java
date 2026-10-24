package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Instant;

public record SubagentSnapshot(String agentId, String agentType, String activity, boolean running, Instant startedAt) {

    SubagentSnapshot stopped() {
        return new SubagentSnapshot(agentId, agentType, activity, false, startedAt);
    }

    SubagentSnapshot withActivity(String text) {
        return new SubagentSnapshot(agentId, agentType, text, running, startedAt);
    }
}
