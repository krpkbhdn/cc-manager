package com.github.krpkbhdn.ccmanager.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.github.CheckSummary;
import com.github.krpkbhdn.ccmanager.github.PullRequest;
import com.github.krpkbhdn.ccmanager.github.PullRequestDraft;
import com.github.krpkbhdn.ccmanager.github.PullRequestState;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MergeRequestServiceTest {

    private static final String LIST_ARGS = "--source-branch=feature";
    private final List<List<String>> commands = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();
    private final List<Path> descriptionFiles = new ArrayList<>();

    private MergeRequestService service(Function<List<String>, CommandRunner.Result> answer) {
        return new MergeRequestService(new GlabCli(GlabCliTest.PROPERTIES, (command, dir, timeout) -> {
            commands.add(command);
            var option = command.stream().filter(argument -> argument.startsWith("--description-file=")).findFirst();
            if (option.isPresent()) {
                var file = Path.of(option.get().substring("--description-file=".length()));
                descriptionFiles.add(file);
                descriptions.add(Files.readString(file));
            }
            return answer.apply(command);
        }));
    }

    private static CommandRunner.Result ok(String stdout) {
        return new CommandRunner.Result(0, stdout, "", false);
    }

    @Test
    void findOpenedMergeRequestReadsPipelineFromViewWhenListHasNone() {
        var list = "[{\"iid\":5,\"state\":\"closed\",\"sha\":\"old\"},{\"iid\":3,\"state\":\"opened\",\"web_url\":\"https://gl/mr/3\"}]";
        var view = "{\"iid\":3,\"state\":\"opened\",\"web_url\":\"https://gl/mr/3\",\"target_branch\":\"develop\",\"sha\":\"abc\",\"head_pipeline\":{\"status\":\"failed\"}}";
        var lookup = service(command -> ok(command.contains("list") ? list : view)).find(Path.of("."), "g/p", "feature");
        assertThat(lookup.pullRequest()).contains(new PullRequest(3, "https://gl/mr/3", PullRequestState.OPEN, CheckSummary.FAILING, "develop", "abc"));
        assertThat(commands).containsExactly(
                List.of("glab", "mr", "list", LIST_ARGS, "--all", "--per-page=100", "--output=json", "-R", "g/p"),
                List.of("glab", "mr", "view", "3", "--output=json", "-R", "g/p"));
    }

    @Test
    void findSkipsViewWhenListAlreadyHasPipelineAndPicksNewestWhenNoneOpened() {
        var withPipeline = "[{\"iid\":2,\"state\":\"opened\",\"head_pipeline\":{\"status\":\"running\"}}]";
        assertThat(service(command -> ok(withPipeline)).find(Path.of("."), "g/p", "feature").pullRequest().orElseThrow().checks()).isEqualTo(CheckSummary.PENDING);
        assertThat(commands).hasSize(1);
        var closed = "[{\"iid\":1,\"state\":\"closed\"},{\"iid\":4,\"state\":\"merged\",\"sha\":\"s\"},{\"iid\":2,\"state\":\"closed\"}]";
        var pr = service(command -> ok(closed)).find(Path.of("."), "g/p", "feature").pullRequest().orElseThrow();
        assertThat(pr.number()).isEqualTo(4);
        assertThat(pr.state()).isEqualTo(PullRequestState.MERGED);
        assertThat(commands).hasSize(2);
    }

    @Test
    void findTellsNoMergeRequestFromFailure() {
        assertThat(service(command -> ok("[]")).find(Path.of("."), "g/p", "feature").pullRequest()).isEmpty();
        assertThat(service(command -> ok("[]")).find(Path.of("."), "g/p", "feature").failed()).isFalse();
        assertThat(service(command -> new CommandRunner.Result(1, "", "dial tcp", false)).find(Path.of("."), "g/p", "feature").failed()).isTrue();
        assertThat(service(command -> ok("not json")).find(Path.of("."), "g/p", "feature").failed()).isTrue();
        assertThat(service(command -> command.contains("view") ? new CommandRunner.Result(1, "", "", false) : ok("[{\"iid\":1,\"state\":\"opened\"}]"))
                .find(Path.of("."), "g/p", "feature").failed()).isTrue();
    }

    @Test
    void createPassesDescriptionThroughDeletedTempFileAndReturnsLastUrlLine() throws IOException {
        var out = "Creating merge request for feature into main in g/p\n\n!9 T\nhttps://gl/g/p/-/merge_requests/9\n";
        var url = service(command -> ok(out)).create(Path.of("."), new PullRequestDraft("g/p", "main", "feature", "Title", "line1\nline2"));
        assertThat(url).isEqualTo("https://gl/g/p/-/merge_requests/9");
        var command = commands.getFirst();
        assertThat(command).containsSubsequence("glab", "mr", "create", "--source-branch=feature", "--target-branch=main", "--title=Title");
        assertThat(command).containsSubsequence("--yes", "--push=false", "-R", "g/p");
        assertThat(command.stream().filter(argument -> argument.startsWith("--description-file=")).count()).isEqualTo(1);
        assertThat(descriptions).containsExactly("line1\nline2");
        assertThat(descriptionFiles.getFirst()).doesNotExist();
    }

    @Test
    void createTakesTheUrlFromStderrToo() throws IOException {
        var url = service(command -> new CommandRunner.Result(0, "", "http://gl.local/g/p/-/merge_requests/3\n", false))
                .create(Path.of("."), new PullRequestDraft("g/p", "main", "feature", "Title", ""));
        assertThat(url).isEqualTo("http://gl.local/g/p/-/merge_requests/3");
    }

    @Test
    void createFailsWithoutUrlOrOnErrorAndRefusesMultiLineTitle() {
        var draft = new PullRequestDraft("g/p", "main", "feature", "Title", "");
        assertThatThrownBy(() -> service(command -> ok("done")).create(Path.of("."), draft)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> service(command -> new CommandRunner.Result(1, "", "run glab auth login", false)).create(Path.of("."), draft))
                .isInstanceOf(IOException.class).hasMessageContaining("glab mr create").hasMessageContaining("glab auth login");
        assertThatThrownBy(() -> service(command -> ok("https://x")).create(Path.of("."), new PullRequestDraft("g/p", "main", "feature", "a\nb", "")))
                .isInstanceOf(IOException.class);
    }
}
