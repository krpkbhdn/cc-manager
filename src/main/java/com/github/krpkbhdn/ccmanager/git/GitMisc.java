package com.github.krpkbhdn.ccmanager.git;

import java.time.Instant;

/** Small value types and option enums shared by the git services. */
public final class GitMisc {

    private GitMisc() {}

    public record Stash(int index, String message, String branch, Instant date) {}

    public record Tag(String name, String commit, String message, boolean annotated) {}

    public record Remote(String name, String fetchUrl, String pushUrl) {}

    /** user.name / user.email as git resolves them; null when unset (commit would fail). */
    public record Identity(String name, String email) {}

    public record CommitRequest(String message, boolean amend, boolean signOff, boolean skipHooks) {}

    public record PushOptions(boolean forceWithLease, boolean setUpstream, boolean tags) {}

    public enum MergeMode { DEFAULT, NO_FAST_FORWARD, FAST_FORWARD_ONLY, SQUASH }

    public enum PullMode { MERGE, REBASE, FAST_FORWARD_ONLY }

    public enum ResetMode { SOFT, MIXED, HARD }

    /** Which side wins when resolving a conflicted file wholesale. */
    public enum ConflictSide { OURS, THEIRS }
}
