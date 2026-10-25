package com.github.krpkbhdn.ccmanager.claude.proposal;

import java.time.Instant;
import tools.jackson.databind.node.ObjectNode;

/** ExitPlanMode. The plan may be empty: Claude then sends no tool input at all. */
public record PlanProposal(String id, String ccmSessionId, Instant createdAt, String agentId, ObjectNode input, String plan,
        String planFilePath) implements Proposal {

    public static final String TOOL_NAME = "ExitPlanMode";

    @Override
    public String toolName() {
        return TOOL_NAME;
    }
}
