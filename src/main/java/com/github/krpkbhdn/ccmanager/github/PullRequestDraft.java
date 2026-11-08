package com.github.krpkbhdn.ccmanager.github;

/** @param repo {@code owner/name} the pull request is opened in, always passed to gh explicitly */
public record PullRequestDraft(String repo, String base, String head, String title, String body) {
}
