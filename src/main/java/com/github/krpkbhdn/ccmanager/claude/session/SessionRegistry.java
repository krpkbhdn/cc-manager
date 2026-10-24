package com.github.krpkbhdn.ccmanager.claude.session;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Live sessions and their status, derived from hook events and process lifecycle. Thread-safe. */
@Component
public class SessionRegistry {

    /** Creates a live session in IDLE state and returns its ccm id. */
    public String register(long projectId) { throw todo(); }

    /** Process exited: the session leaves the registry. */
    public void markEnded(String ccmSessionId, int exitCode) { throw todo(); }

    /** User pressed Esc in the terminal: Claude fires no Stop on interrupt, so WORKING becomes IDLE here. */
    public void markInterrupted(String ccmSessionId) { throw todo(); }

    public List<SessionSnapshot> sessions() { throw todo(); }

    public List<SessionSnapshot> sessions(long projectId) { throw todo(); }

    public Optional<SessionSnapshot> find(String ccmSessionId) { throw todo(); }

    /** Max urgency over the project's live sessions; OFFLINE when there are none. */
    public AgentStatus projectStatus(long projectId) { throw todo(); }

    /** @return unsubscribe handle; callback fires (any thread) after any session or status change */
    public Runnable subscribe(Runnable onChange) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
