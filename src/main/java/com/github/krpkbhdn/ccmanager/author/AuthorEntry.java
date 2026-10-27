package com.github.krpkbhdn.ccmanager.author;

import java.util.List;

/**
 * One installable component as shown to the user; {@code projectId} is 0 unless the component is per project,
 * {@code installedVersion} 0 when none, {@code changes} the notes of the versions an update brings,
 * {@code replacesExisting} true when installing overwrites a team the user already has.
 */
public record AuthorEntry(AuthorComponent component, long projectId, String title, String summary, int bundledVersion, int installedVersion,
        AuthorStatus status, List<String> changes, boolean replacesExisting) {

    public AuthorEntry {
        changes = List.copyOf(changes);
    }
}
