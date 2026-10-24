package com.github.krpkbhdn.ccmanager.claude.hook;

import org.springframework.stereotype.Component;

/**
 * Loopback-only HTTP endpoint that Claude Code http hooks (and a curl command hook for SessionStart) post to.
 * Parses each call into a HookEvent, persists it and hands it to the session registry.
 */
@Component
public class HookServer {

    public int port() { throw new UnsupportedOperationException("TODO"); }
}
