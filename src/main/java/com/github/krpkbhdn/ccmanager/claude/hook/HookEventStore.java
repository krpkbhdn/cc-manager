package com.github.krpkbhdn.ccmanager.claude.hook;

import java.util.List;
import org.springframework.stereotype.Component;

/** Persistent log of all hook events (SQLite), capped by retention. */
@Component
public class HookEventStore {

    /** Newest last. */
    public List<ActivityEntry> recent(String ccmSessionId, int limit) { throw new UnsupportedOperationException("TODO"); }
}
