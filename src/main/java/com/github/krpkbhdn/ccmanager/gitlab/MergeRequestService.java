package com.github.krpkbhdn.ccmanager.gitlab;

import com.github.krpkbhdn.ccmanager.github.PullRequestDraft;
import com.github.krpkbhdn.ccmanager.github.PullRequestLookup;
import com.github.krpkbhdn.ccmanager.github.PullRequestService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Looks up and opens GitLab merge requests through the glab CLI, reported with the pull request types. */
@Service
public class MergeRequestService {

    private static final String HTTPS = "https://";
    private static final String HTTP = "http://";

    private final GlabCli glab;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public MergeRequestService(GlabCli glab) {
        this.glab = glab;
    }

    /** Tells "no merge request for the branch" from a glab failure; an opened one wins over newer closed ones. */
    public PullRequestLookup find(Path directory, String repository, String branch) {
        try {
            var list = glab.run(directory, "mr", "list", "--source-branch=" + branch, "--all", "--per-page=100", "--output=json", "-R", repository);
            if (!list.succeeded()) {
                return PullRequestLookup.failure();
            }
            var chosen = choose(mapper.readTree(list.stdout()));
            if (chosen.isEmpty()) {
                return PullRequestLookup.none();
            }
            var merge = chosen.get();
            if (MergeRequestJson.isOpened(merge) && !MergeRequestJson.hasPipeline(merge)) {
                var view = glab.run(directory, "mr", "view", Integer.toString(merge.path("iid").asInt()), "--output=json", "-R", repository);
                if (!view.succeeded()) {
                    return PullRequestLookup.failure();
                }
                merge = mapper.readTree(view.stdout());
            }
            return PullRequestLookup.found(MergeRequestJson.parse(merge));
        } catch (IOException | RuntimeException e) {
            return PullRequestLookup.failure();
        }
    }

    private static boolean isUrl(String line) {
        return line.startsWith(HTTPS) || line.startsWith(HTTP);
    }

    private static Optional<JsonNode> choose(JsonNode list) {
        var newestFirst = Comparator.comparingInt((JsonNode merge) -> merge.path("iid").asInt()).reversed();
        var all = StreamSupport.stream(list.spliterator(), false).sorted(newestFirst).toList();
        return all.stream().filter(MergeRequestJson::isOpened).findFirst().or(() -> all.stream().findFirst());
    }

    /** @return the URL of the new merge request; the description goes through a file because quoting a multi-line argument is fragile on Windows */
    public String create(Path directory, PullRequestDraft draft) throws IOException {
        if (draft.title().chars().anyMatch(Character::isISOControl)) {
            throw new IOException("The title must be one line without control characters");
        }
        var descriptionFile = Files.createTempFile("ccm-mr-description", ".md");
        try {
            Files.writeString(descriptionFile, draft.body(), StandardCharsets.UTF_8);
            var result = glab.run(directory, "mr", "create", "--source-branch=" + draft.head(), "--target-branch=" + draft.base(),
                    "--title=" + draft.title(), "--description-file=" + descriptionFile, "--yes", "--push=false", "-R", draft.repo());
            if (!result.succeeded()) {
                throw new IOException(PullRequestService.failureMessage("glab mr create", "glab auth login", result.exitCode(), result.stderr()));
            }
            return (result.stdout() + "\n" + result.stderr()).lines().map(String::strip).filter(MergeRequestService::isUrl).reduce((first, second) -> second)
                    .orElseThrow(() -> new IOException("glab mr create did not print the merge request URL"));
        } finally {
            Files.deleteIfExists(descriptionFile);
        }
    }
}
