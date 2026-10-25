package com.github.krpkbhdn.ccmanager.claude.session;

/** Lifecycle of one subagent; the first two are live, the rest are final. */
public enum SubagentState {
    WORKING,
    NEEDS_ACTION,
    DONE,
    FAILED,
    INTERRUPTED;

    public boolean live() {
        return this == WORKING || this == NEEDS_ACTION;
    }
}
