package com.github.krpkbhdn.ccmanager.github;

/** {@code headRefOid} is the commit the pull request's head branch pointed to; empty when gh did not say. */
public record PullRequest(int number, String url, PullRequestState state, CheckSummary checks, String baseRef, String headRefOid) {

    public PullRequest(int number, String url, PullRequestState state, CheckSummary checks, String baseRef) {
        this(number, url, state, checks, baseRef, "");
    }
}
