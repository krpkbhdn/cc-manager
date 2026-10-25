package com.github.krpkbhdn.ccmanager.claude.proposal;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** An MCP server asking the user for input (Elicitation). url and elicitationId are empty when absent. Not verified against a live claude. */
public record FormProposal(String id, String ccmSessionId, Instant createdAt, String agentId, String serverName, String message,
        String mode, String url, String elicitationId, JsonNode requestedSchema) implements Proposal {

    @Override
    public String toolName() {
        return "";
    }

    @Override
    public boolean settledBy(HookEvent event) {
        return Proposal.super.settledBy(event) || event.type() == HookEventType.ELICITATION_RESULT;
    }
}
