package com.github.krpkbhdn.ccmanager.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.github.CheckSummary;
import com.github.krpkbhdn.ccmanager.github.PullRequest;
import com.github.krpkbhdn.ccmanager.github.PullRequestState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class MergeRequestJsonTest {

    private static PullRequest parse(String json) {
        return MergeRequestJson.parse(JsonMapper.builder().build().readTree(json));
    }

    @Test
    void squashMergedUsesHeadShaNotTheMergeOrSquashCommit() {
        var pr = parse("{\"iid\":7,\"web_url\":\"https://gl/mr/7\",\"state\":\"merged\",\"target_branch\":\"develop\","
                + "\"sha\":\"aaa111\",\"merge_commit_sha\":\"bbb222\",\"squash_commit_sha\":\"ccc333\",\"head_pipeline\":{\"status\":\"success\"}}");
        assertThat(pr).isEqualTo(new PullRequest(7, "https://gl/mr/7", PullRequestState.MERGED, CheckSummary.PASSING, "develop", "aaa111"));
    }

    @ParameterizedTest
    @CsvSource({"opened,OPEN", "merged,MERGED", "closed,CLOSED", "locked,CLOSED"})
    void mapsState(String gitlab, PullRequestState expected) {
        assertThat(parse("{\"iid\":1,\"state\":\"" + gitlab + "\"}").state()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"success,PASSING", "skipped,PASSING", "failed,FAILING", "canceled,FAILING", "running,PENDING", "pending,PENDING", "manual,PENDING"})
    void mapsPipelineStatus(String status, CheckSummary expected) {
        assertThat(parse("{\"iid\":1,\"state\":\"opened\",\"head_pipeline\":{\"status\":\"" + status + "\"}}").checks()).isEqualTo(expected);
    }

    @Test
    void absentOrNullPipelineMeansNoChecks() {
        assertThat(parse("{\"iid\":1,\"state\":\"opened\"}").checks()).isEqualTo(CheckSummary.NONE);
        assertThat(parse("{\"iid\":1,\"state\":\"opened\",\"head_pipeline\":null}").checks()).isEqualTo(CheckSummary.NONE);
    }
}
