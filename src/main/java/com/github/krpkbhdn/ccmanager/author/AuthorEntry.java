package com.github.krpkbhdn.ccmanager.author;

import java.util.List;

/**
 * One catalog item as shown to the user: {@code installedVersion} 0 when none,
 * {@code changes} the notes of the versions an update brings, {@code replacesExisting} true when installing overwrites a team the user already has.
 */
public record AuthorEntry(AuthorItem item, int installedVersion, AuthorStatus status, List<String> changes,
        boolean replacesExisting) {

    public AuthorEntry {
        changes = List.copyOf(changes);
    }
}
