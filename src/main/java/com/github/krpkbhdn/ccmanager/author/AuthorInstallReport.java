package com.github.krpkbhdn.ccmanager.author;

import java.util.List;
import java.util.Optional;

/**
 * Result of installing the chosen components. The handoff is present when Claude should finish the setup; the entries
 * Claude finishes stay in {@code awaitingSession} until the session started and {@code markInstalled} records them.
 */
public record AuthorInstallReport(List<ComponentOutcome> components, Optional<SessionHandoff> handoff, List<AuthorEntry> awaitingSession) {

    public AuthorInstallReport {
        components = List.copyOf(components);
        awaitingSession = List.copyOf(awaitingSession);
    }
}
