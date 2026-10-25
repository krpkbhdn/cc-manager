package com.github.krpkbhdn.ccmanager.author;

/** One installable component as shown to the user; {@code projectId} is 0 unless the component is per project, {@code installedVersion} 0 when none. */
public record AuthorEntry(AuthorComponent component, long projectId, String title, String summary, int bundledVersion, int installedVersion,
        AuthorStatus status) {}
