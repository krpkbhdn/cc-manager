package com.github.krpkbhdn.ccmanager.team;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;

/** A stored role; the lead is the main session, every other role becomes a native Claude Code subagent. */
public record TeamRole(long id, RoleDraft draft, int position) {

    public String name() {
        return draft.name();
    }

    public boolean lead() {
        return draft.lead();
    }

    public ModelSelection selection() {
        return draft.selection();
    }
}
