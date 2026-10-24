package com.github.krpkbhdn.ccmanager.claude.session;

/** Traffic light of a session or project. Declared in ascending urgency, so aggregation is max. */
public enum AgentStatus {
    /** No live Claude process (grey). */
    OFFLINE,
    /** Waiting for the next prompt (green). */
    IDLE,
    /** Working (yellow). */
    WORKING,
    /** Needs a user decision: permission, question, plan approval (red). */
    NEEDS_ACTION
}
