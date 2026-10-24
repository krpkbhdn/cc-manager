package com.github.krpkbhdn.ccmanager.git;

/** A failed git command; {@link #kind()} lets the UI offer the right next step, the message is user-readable. */
public class GitException extends RuntimeException {

    public enum Kind {
        NOT_A_REPOSITORY, AUTH, CONFLICT, DIRTY_TREE, NOT_FOUND, HOOK_FAILED, NOTHING_TO_COMMIT, NO_UPSTREAM,
        NON_FAST_FORWARD, LOCKED, TIMEOUT, GIT_MISSING, OTHER, IDENTITY_UNKNOWN
    }

    private final Kind kind;
    private final String output;

    public GitException(Kind kind, String message, String output) {
        super(message);
        this.kind = kind;
        this.output = output;
    }

    public Kind kind() {
        return kind;
    }

    /** Raw stderr/stdout of the failed command, for a details view. */
    public String output() {
        return output;
    }
}
