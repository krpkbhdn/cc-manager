package com.github.krpkbhdn.ccmanager.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PullRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private PullRequest parse(String rollup, String state) {
        return PullRequestJson.parse(mapper.readTree("{\"number\":7,\"url\":\"https://x/pull/7\",\"state\":\"" + state
                + "\",\"baseRefName\":\"main\",\"headRefOid\":\"abc123\",\"statusCheckRollup\":" + rollup + "}"));
    }

    @Test
    void readsFieldsAndNoChecks() {
        assertThat(parse("[]", "OPEN")).isEqualTo(new PullRequest(7, "https://x/pull/7", PullRequestState.OPEN, CheckSummary.NONE, "main", "abc123"));
    }

    @Test
    void mergedStateIsRead() {
        assertThat(parse("[]", "MERGED").state()).isEqualTo(PullRequestState.MERGED);
    }

    @Test
    void passingWhenAllSuccessNeutralSkippedOrStatusSuccess() {
        var rollup = "[{\"status\":\"COMPLETED\",\"conclusion\":\"SUCCESS\"},{\"status\":\"COMPLETED\",\"conclusion\":\"SKIPPED\"},"
                + "{\"status\":\"COMPLETED\",\"conclusion\":\"NEUTRAL\"},{\"state\":\"SUCCESS\"}]";
        assertThat(parse(rollup, "OPEN").checks()).isEqualTo(CheckSummary.PASSING);
    }

    @Test
    void pendingWhenACheckRunIsNotCompleted() {
        var rollup = "[{\"status\":\"COMPLETED\",\"conclusion\":\"SUCCESS\"},{\"status\":\"IN_PROGRESS\",\"conclusion\":\"\"}]";
        assertThat(parse(rollup, "OPEN").checks()).isEqualTo(CheckSummary.PENDING);
    }

    @Test
    void pendingWhenStatusContextIsPending() {
        assertThat(parse("[{\"state\":\"PENDING\"}]", "OPEN").checks()).isEqualTo(CheckSummary.PENDING);
    }

    @Test
    void failingBeatsPending() {
        var rollup = "[{\"status\":\"IN_PROGRESS\",\"conclusion\":\"\"},{\"status\":\"COMPLETED\",\"conclusion\":\"TIMED_OUT\"}]";
        assertThat(parse(rollup, "OPEN").checks()).isEqualTo(CheckSummary.FAILING);
    }

    @Test
    void failingOnStatusContextErrorAndCancelledRun() {
        assertThat(parse("[{\"state\":\"ERROR\"}]", "OPEN").checks()).isEqualTo(CheckSummary.FAILING);
        assertThat(parse("[{\"status\":\"COMPLETED\",\"conclusion\":\"CANCELLED\"}]", "OPEN").checks()).isEqualTo(CheckSummary.FAILING);
    }
}
