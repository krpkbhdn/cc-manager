package com.github.krpkbhdn.ccmanager.claude.proposal;

import com.github.krpkbhdn.ccmanager.claude.hook.HookEvent;
import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType;
import java.time.Instant;
import java.util.Set;

/** A decision Claude Code waits for, held open as a hook call and answerable from the app instead of the terminal dialog. */
public sealed interface Proposal permits QuestionsProposal, PlanProposal, PermissionProposal, FormProposal {

    Set<HookEventType> ENDS_ANY_PROPOSAL = Set.of(HookEventType.STOP, HookEventType.STOP_FAILURE,
            HookEventType.USER_PROMPT_SUBMIT, HookEventType.SESSION_END);
    Set<HookEventType> ENDS_PROPOSAL_OF_SAME_TOOL = Set.of(HookEventType.POST_TOOL_USE, HookEventType.POST_TOOL_USE_FAILURE,
            HookEventType.PERMISSION_DENIED);

    String id();

    String ccmSessionId();

    Instant createdAt();

    /** Id of the subagent that asks, empty for the main agent. */
    String agentId();

    /** Name of the tool the decision is about, empty for an MCP form. */
    String toolName();

    /** True when the matter was settled elsewhere (terminal, turn end), so the held call is moot. The caller matches the session. */
    default boolean settledBy(HookEvent event) {
        return ENDS_ANY_PROPOSAL.contains(event.type())
                || ENDS_PROPOSAL_OF_SAME_TOOL.contains(event.type()) && event.text("tool_name").equals(toolName())
                        && event.agentId().equals(agentId());
    }
}
