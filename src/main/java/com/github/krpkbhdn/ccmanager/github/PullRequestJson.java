package com.github.krpkbhdn.ccmanager.github;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Reads the output of gh pr view; its statusCheckRollup mixes CheckRun and StatusContext entries. */
final class PullRequestJson {

    private static final Set<String> FAILED = Set.of("FAILURE", "ERROR", "CANCELLED", "TIMED_OUT", "STARTUP_FAILURE", "ACTION_REQUIRED");
    private static final Set<String> PASSED = Set.of("SUCCESS", "NEUTRAL", "SKIPPED");

    private PullRequestJson() {
    }

    static PullRequest parse(JsonNode json) {
        return new PullRequest(json.path("number").asInt(), json.path("url").asString(""),
                PullRequestState.valueOf(json.path("state").asString("OPEN").toUpperCase(Locale.ROOT)),
                summarize(json.path("statusCheckRollup")), json.path("baseRefName").asString(""),
                json.path("headRefOid").asString(""));
    }

    static CheckSummary summarize(JsonNode rollup) {
        var pending = false;
        var any = false;
        for (var check : rollup) {
            any = true;
            var outcome = outcome(check);
            if (FAILED.contains(outcome)) {
                return CheckSummary.FAILING;
            }
            pending |= !PASSED.contains(outcome);
        }
        if (!any) {
            return CheckSummary.NONE;
        }
        return pending ? CheckSummary.PENDING : CheckSummary.PASSING;
    }

    /** A CheckRun carries status and conclusion (empty until completed), a StatusContext carries state. */
    private static String outcome(JsonNode check) {
        var state = check.path("state").asString("");
        if (!state.isEmpty()) {
            return state.toUpperCase(Locale.ROOT);
        }
        if (!"COMPLETED".equalsIgnoreCase(check.path("status").asString(""))) {
            return "PENDING";
        }
        return check.path("conclusion").asString("").toUpperCase(Locale.ROOT);
    }
}
