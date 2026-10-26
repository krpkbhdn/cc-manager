package com.github.krpkbhdn.ccmanager.claude.proposal;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Any other tool that needs approval. Suggestions are Claude's own "allow and remember" options, kept verbatim to be sent back. */
public record PermissionProposal(String id, String ccmSessionId, Instant createdAt, String agentId, String toolName, JsonNode toolInput,
        List<Suggestion> suggestions) implements Proposal {

    public PermissionProposal {
        suggestions = List.copyOf(suggestions);
    }

    /** Parallel prompts of one tool differ by input, so the tool's own events only settle the prompt with the same input. */
    @Override
    public boolean settledBy(HookEvent event) {
        if (!Proposal.super.settledBy(event)) {
            return false;
        }
        // shortcut: if Claude rewrites tool_input between PermissionRequest and PostToolUse the dialog stays until Stop/next prompt, verify with a live run
        return !ENDS_PROPOSAL_OF_SAME_TOOL.contains(event.type()) || toolInput.equals(event.payload().path("tool_input"));
    }

    public record Suggestion(JsonNode raw, String description) {}
}
