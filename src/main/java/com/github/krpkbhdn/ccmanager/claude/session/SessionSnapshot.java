package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Instant;
import java.util.List;

/** Immutable view of one live Claude process. Nullable: claudeSessionId, activity, permissionMode, model, title, error. */
public record SessionSnapshot(
        String ccmSessionId,
        long projectId,
        String claudeSessionId,
        AgentStatus status,
        String activity,
        String permissionMode,
        String model,
        String title,
        Instant startedAt,
        Instant lastEventAt,
        List<SubagentSnapshot> subagents,
        int backgroundTasks,
        String error) {}
