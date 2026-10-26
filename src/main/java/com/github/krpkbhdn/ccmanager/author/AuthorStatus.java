package com.github.krpkbhdn.ccmanager.author;

import java.util.Optional;

/** Where a component stands against the bundled version. */
public enum AuthorStatus {
    NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, DECLINED;

    public static AuthorStatus of(Optional<AuthorDecision> decision, int bundledVersion) {
        return decision.map(found -> switch (found.choice()) {
            case DECLINED -> DECLINED;
            case INSTALLED -> found.version() < bundledVersion ? UPDATE_AVAILABLE : INSTALLED;
        }).orElse(NOT_INSTALLED);
    }

    /** Whether the user should be asked about the component unprompted. */
    public boolean offered() {
        return this == NOT_INSTALLED || this == UPDATE_AVAILABLE;
    }
}
