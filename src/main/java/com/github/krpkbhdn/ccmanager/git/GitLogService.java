package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.workspace.UnifiedDiffParser;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * History with a computed lane layout (see {@link GitLog.Row}) and per-commit details.
 *
 * <p>Infinite scroll: pass the {@link GraphState} returned with the previous page to {@link #log(Path, GitLog.Query,
 * GraphState)} and the graph continues seamlessly. Without it, a page with {@code skip > 0} replays the history from the
 * start (cheap: no diffs, bodies capped) and slices. Filtered histories (author, text, path) get a single lane, because
 * their parents are mostly missing from the list.
 */
@Service
public class GitLogService {

    /** A page together with the graph state to hand to the next page (null for filtered, graph-less pages). */
    public record StatefulPage(GitLog.Page page, GraphState next) {}

    private final GitRunner git;

    @Autowired
    public GitLogService(GitRunner git) {
        this.git = git;
    }

    public GitLog.Page log(Path repo, GitLog.Query query) {
        return log(repo, query, null).page();
    }

    public StatefulPage log(Path repo, GitLog.Query query, GraphState previous) {
        var limit = Math.max(query.limit(), 1);
        var filtered = isFiltered(query);
        var replay = !filtered && previous == null && query.skip() > 0;
        var wanted = (replay ? query.skip() : 0) + limit;
        var commits = fetch(repo, query, replay ? 0 : query.skip(), wanted + 1);
        var hasMore = commits.size() > wanted;
        if (filtered) {
            var visible = commits.subList(0, Math.min(commits.size(), limit));
            return new StatefulPage(new GitLog.Page(GitGraphLayout.linear(visible), hasMore), null);
        }
        var laidOut = GitGraphLayout.layout(commits, previous, wanted);
        var rows = laidOut.rows().subList(replay ? query.skip() : 0, Math.min(laidOut.rows().size(), wanted));
        return new StatefulPage(new GitLog.Page(List.copyOf(rows), hasMore), laidOut.stateAfterMarked());
    }

    public GitLog.Details details(Path repo, String hash) {
        GitRevisions.requireNotOption(hash);
        var commit = GitLogParser.parse(git.out(repo, "log", "-1", "--decorate=full", GitLogParser.FORMAT, hash), Integer.MAX_VALUE)
                .getFirst();
        var diff = git.out(repo, "show", "--format=", "-M", "--no-color", "--no-ext-diff", "-m", "--first-parent", "--root", hash);
        var files = UnifiedDiffParser.parse(diff, UnifiedDiffParser.MAX_LINES_PER_FILE, UnifiedDiffParser.MAX_TOTAL_LINES);
        return new GitLog.Details(commit, files);
    }

    private List<GitLog.Commit> fetch(Path repo, GitLog.Query query, int skip, int count) {
        var args = new ArrayList<>(List.of("log", GitLogParser.FORMAT, "--date-order", "--decorate=full", "--skip=" + skip,
                "--max-count=" + count));
        addFilters(args, query);
        addRevisions(repo, args, query);
        return GitLogParser.parse(git.out(repo, args.toArray(String[]::new)));
    }

    private static void addFilters(List<String> args, GitLog.Query query) {
        if (!isBlank(query.author())) {
            args.add("--author=" + query.author());
        }
        if (!isBlank(query.text())) {
            args.add("--grep=" + query.text());
        }
        if (!isBlank(query.author()) || !isBlank(query.text())) {
            args.addAll(List.of("-i", "--fixed-strings"));
        }
    }

    private void addRevisions(Path repo, List<String> args, GitLog.Query query) {
        if (isBlank(query.branch())) {
            args.addAll(List.of("--exclude=refs/stash", "--all"));
        } else {
            GitRevisions.requireNotOption(query.branch());
            args.add(query.branch());
        }
        if (!isBlank(query.path())) {
            var existingFile = isExistingFile(repo, query.path());
            if (existingFile || isSingleFile(query.path())) {
                args.add("--follow");
            }
            args.addAll(List.of("--", existingFile ? ":(literal)" + query.path() : query.path()));
        }
    }

    private static boolean isExistingFile(Path repo, String path) {
        try {
            return Files.isRegularFile(repo.resolve(path));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static boolean isSingleFile(String path) {
        return !path.endsWith("/") && path.chars().noneMatch(c -> c == '*' || c == '?' || c == '[');
    }

    private static boolean isFiltered(GitLog.Query query) {
        return !isBlank(query.author()) || !isBlank(query.text()) || !isBlank(query.path());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
