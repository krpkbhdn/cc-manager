package com.github.krpkbhdn.ccmanager.github;

import java.util.Optional;

/** Result of asking gh for a branch's pull request; a failed lookup is not "no pull request". */
public record PullRequestLookup(Optional<PullRequest> pullRequest, boolean failed) {

    public static PullRequestLookup none() {
        return new PullRequestLookup(Optional.empty(), false);
    }

    public static PullRequestLookup found(PullRequest pullRequest) {
        return new PullRequestLookup(Optional.of(pullRequest), false);
    }

    public static PullRequestLookup failure() {
        return new PullRequestLookup(Optional.empty(), true);
    }
}
