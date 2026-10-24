package com.github.krpkbhdn.ccmanager.git;

import java.time.Instant;

/** A local or remote-tracking branch. {@code name} of a remote branch includes the remote ("origin/main"). */
public record GitBranch(
        String name,
        boolean remote,
        boolean current,
        String upstream,
        int ahead,
        int behind,
        String commit,
        String subject,
        Instant date) {}
