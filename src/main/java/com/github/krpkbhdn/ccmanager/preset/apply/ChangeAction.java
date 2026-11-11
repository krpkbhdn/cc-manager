package com.github.krpkbhdn.ccmanager.preset.apply;

/** What applying one component would do to the project. */
public enum ChangeAction {
    CREATE, UPDATE, INSTALL, PRESENT, CONFLICT, NEEDS_CONFIRMATION;

    boolean writes() {
        return this == CREATE || this == UPDATE || this == INSTALL;
    }
}
