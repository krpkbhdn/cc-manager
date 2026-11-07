package com.github.krpkbhdn.ccmanager.gitlab;

import com.github.krpkbhdn.ccmanager.github.CheckSummary;
import com.github.krpkbhdn.ccmanager.github.PullRequest;
import com.github.krpkbhdn.ccmanager.github.PullRequestState;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** Reads a merge request from glab's JSON; the head commit is {@code sha}, never the merge or squash commit. */
final class MergeRequestJson {

    private MergeRequestJson() {
    }

    static PullRequest parse(JsonNode json) {
        return new PullRequest(json.path("iid").asInt(), json.path("web_url").asString(""), state(json.path("state").asString("opened")),
                checks(json.path("head_pipeline")), json.path("target_branch").asString(""), json.path("sha").asString(""));
    }

    static boolean isOpened(JsonNode json) {
        return state(json.path("state").asString("opened")) == PullRequestState.OPEN;
    }

    static boolean hasPipeline(JsonNode json) {
        return json.path("head_pipeline").isObject();
    }

    private static PullRequestState state(String gitlabState) {
        return switch (gitlabState.toLowerCase(Locale.ROOT)) {
            case "opened" -> PullRequestState.OPEN;
            case "merged" -> PullRequestState.MERGED;
            default -> PullRequestState.CLOSED;
        };
    }

    private static CheckSummary checks(JsonNode pipeline) {
        if (!pipeline.isObject()) {
            return CheckSummary.NONE;
        }
        return switch (pipeline.path("status").asString("").toLowerCase(Locale.ROOT)) {
            case "success", "skipped" -> CheckSummary.PASSING;
            case "failed", "canceled" -> CheckSummary.FAILING;
            default -> CheckSummary.PENDING;
        };
    }
}
