package com.github.krpkbhdn.ccmanager.github;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Looks up and opens GitHub pull requests through the gh CLI. */
@Service
public class PullRequestService {

    private static final String VIEW_FIELDS = "number,url,state,baseRefName,headRefOid,statusCheckRollup";

    /** Plain words only, short; anything with a URL, path, long token or hex-like run is left out. */
    private static final Pattern SAFE_LINE = Pattern.compile("[A-Za-z ,.'-]{1,100}");

    private static final String NO_PULL_REQUEST = "no pull requests found";

    private final GhCli gh;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public PullRequestService(GhCli gh) {
        this.gh = gh;
    }

    /** Tells "no pull request for the branch" from a gh failure (offline, rate limit). */
    public PullRequestLookup find(Path directory, String branch) {
        try {
            var result = gh.run(directory, "pr", "view", "--json", VIEW_FIELDS, "--", branch);
            if (result.succeeded()) {
                return PullRequestLookup.found(PullRequestJson.parse(mapper.readTree(result.stdout())));
            }
            return !result.timedOut() && result.stderr().toLowerCase(Locale.ROOT).contains(NO_PULL_REQUEST) ? PullRequestLookup.none() : PullRequestLookup.failure();
        } catch (IOException | RuntimeException e) {
            return PullRequestLookup.failure();
        }
    }

    /** @return the URL of the new pull request; the body goes through a file because quoting a multi-line argument is fragile on Windows */
    public String create(Path directory, PullRequestDraft draft) throws IOException {
        requireOneLine(draft.title());
        var bodyFile = Files.createTempFile("ccm-pr-body", ".md");
        try {
            Files.writeString(bodyFile, draft.body(), StandardCharsets.UTF_8);
            var result = gh.run(directory, "pr", "create", "--repo=" + draft.repo(), "--base=" + draft.base(), "--head=" + draft.head(),
                    "--title=" + draft.title(), "--body-file=" + bodyFile);
            if (!result.succeeded()) {
                throw new IOException(failureMessage("gh pr create", "gh auth login", result.exitCode(), result.stderr()));
            }
            return result.stdout().strip();
        } finally {
            Files.deleteIfExists(bodyFile);
        }
    }

    private static void requireOneLine(String title) throws IOException {
        if (title.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("The title must be one line without control characters");
        }
    }

    /** Never carries the body or anything URL- or token-like: only the sign-in hint, a harmless last stderr line, or the exit code. */
    /** @param action the failed command shown to the user; @param signInCommand the command whose mention in stderr means "not signed in" */
    public static String failureMessage(String action, String signInCommand, int exitCode, String stderr) {
        var base = action + " failed with exit code " + exitCode;
        var lines = stderr.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (stderr.toLowerCase(Locale.ROOT).contains(signInCommand)) {
            return base + "; run `" + signInCommand + "` in a terminal";
        }
        return lines.isEmpty() || !SAFE_LINE.matcher(lines.getLast()).matches() ? base : base + ": " + lines.getLast();
    }
}
