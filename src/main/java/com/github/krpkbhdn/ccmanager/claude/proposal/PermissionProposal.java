package com.github.krpkbhdn.ccmanager.claude.proposal;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Any other tool that needs approval. Suggestions are Claude's own "allow and remember" options, kept verbatim to be sent back. */
public record PermissionProposal(String id, String ccmSessionId, Instant createdAt, String agentId, String toolName, JsonNode toolInput,
        List<Suggestion> suggestions) implements Proposal {

    public PermissionProposal {
        suggestions = List.copyOf(suggestions);
    }

    public record Suggestion(JsonNode raw, String description) {}
}
