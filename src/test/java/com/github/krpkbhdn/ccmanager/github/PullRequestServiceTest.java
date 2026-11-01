package com.github.krpkbhdn.ccmanager.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestServiceTest {

    private final List<List<String>> commands = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<Path> bodyFiles = new ArrayList<>();

    private PullRequestService service(int exitCode, String stdout) {
        return new PullRequestService(new GhCli(GhCliTest.PROPERTIES, (command, dir, timeout) -> {
            commands.add(command);
            var option = command.stream().filter(argument -> argument.startsWith("--body-file=")).findFirst();
            if (option.isPresent()) {
                var file = Path.of(option.get().substring("--body-file=".length()));
                bodyFiles.add(file);
                bodies.add(Files.readString(file));
            }
            return new CommandRunner.Result(exitCode, stdout, "", false);
        }));
    }

    @Test
    void findParsesPullRequest() {
        var json = "{\"number\":3,\"url\":\"https://x/pull/3\",\"state\":\"OPEN\",\"baseRefName\":\"develop\",\"statusCheckRollup\":[]}";
        var pr = service(0, json).find(Path.of("."), "feature").pullRequest();
        assertThat(pr).contains(new PullRequest(3, "https://x/pull/3", PullRequestState.OPEN, CheckSummary.NONE, "develop"));
        assertThat(commands.getFirst()).containsExactly("gh", "pr", "view", "feature", "--json", "number,url,state,baseRefName,headRefOid,statusCheckRollup");
    }

    @Test
    void createPassesBodyThroughDeletedTempFile() throws IOException {
        var url = service(0, "https://x/pull/9\n").create(Path.of("."), new PullRequestDraft("o/r", "main", "feature", "Title", "line1\nline2"));
        assertThat(url).isEqualTo("https://x/pull/9");
        assertThat(commands.getFirst()).containsSubsequence("gh", "pr", "create", "--repo=o/r", "--base=main", "--head=feature", "--title=Title");
        assertThat(commands.getFirst().getLast()).startsWith("--body-file=");
        assertThat(bodies).containsExactly("line1\nline2");
        assertThat(bodyFiles.getFirst()).doesNotExist();
    }

    @Test
    void failureHintMapsSignInKeepsPlainLineAndHidesUrlsAndTokens() {
        assertThat(PullRequestService.failureMessage(1, "To get started with GitHub CLI, please run:  gh auth login")).contains("gh auth login");
        assertThat(PullRequestService.failureMessage(1, "x" + System.lineSeparator() + "a pull request already exists")).endsWith(": a pull request already exists");
        assertThat(PullRequestService.failureMessage(1, "see https://github.com/o/r/pull/1")).isEqualTo("gh pr create failed with exit code 1");
        assertThat(PullRequestService.failureMessage(1, "bad token ghp_abc123")).isEqualTo("gh pr create failed with exit code 1");
        assertThat(PullRequestService.failureMessage(2, "")).isEqualTo("gh pr create failed with exit code 2");
    }

    @Test
    void hostileTitleStaysOneOptionValueAndNewlinesAreRefused() throws IOException {
        var hostile = "x\" --base \"evil";
        service(0, "u").create(Path.of("."), new PullRequestDraft("o/r", "main", "feature", hostile, ""));
        assertThat(commands.getFirst()).contains("--title=" + hostile).doesNotContain("--base=evil", "evil");
        assertThatThrownBy(() -> service(0, "u").create(Path.of("."), new PullRequestDraft("o/r", "main", "feature", "a\nb", "")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void findTellsNoPullRequestFromFailure() {
        assertThat(findWith(1, "no pull requests found for branch \"feature\"").failed()).isFalse();
        assertThat(findWith(1, "no pull requests found for branch \"feature\"").pullRequest()).isEmpty();
        assertThat(findWith(1, "error connecting to api.github.com").failed()).isTrue();
        assertThat(findWith(0, "not json").failed()).isTrue();
    }

    private PullRequestLookup findWith(int exitCode, String text) {
        var gh = new GhCli(GhCliTest.PROPERTIES, (command, dir, timeout) ->
                new CommandRunner.Result(exitCode, exitCode == 0 ? text : "", exitCode == 0 ? "" : text, false));
        return new PullRequestService(gh).find(Path.of("."), "feature");
    }
}
