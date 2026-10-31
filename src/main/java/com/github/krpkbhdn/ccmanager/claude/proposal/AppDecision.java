package com.github.krpkbhdn.ccmanager.claude.proposal;

/** How a proposal held for the app ended: the user's answer, or the reason there is none. */
public sealed interface AppDecision {

    record Answered(ProposalAnswer answer) implements AppDecision {}

    enum Unanswered implements AppDecision {
        TIMED_OUT, TOO_MANY_PENDING, RELEASED
    }
}
