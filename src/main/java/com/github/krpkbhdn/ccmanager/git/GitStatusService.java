package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.Identity;
import com.github.krpkbhdn.ccmanager.git.GitMisc.Remote;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import com.github.krpkbhdn.ccmanager.workspace.UnifiedDiffParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Read-only repository state for the Changes view. Never throws for "not a repository": it returns {@link GitStatus#notARepository()}.
 * {@code repo} is the repository root; all paths are relative to it.
 */
@Service
public class GitStatusService {

    static final String TOO_LARGE_MARKER = "File too large to show";

    private static final String NULL_DEVICE = "/dev/null";
    private static final String STASH_REF = "refs/stash";

    private final GitRunner git;

    @Autowired
    public GitStatusService(GitRunner git) {
        this.git = git;
    }

    public GitStatus status(Path repo) {
        var gitDir = Files.isDirectory(repo) ? gitDirectory(repo) : null;
        if (gitDir == null) {
            return GitStatus.notARepository();
        }
        var parsed = PorcelainStatusParser.parse(
                git.out(repo, "status", "--porcelain=v2", "--branch", "-z", "--untracked-files=all"));
        return new GitStatus(true, parsed.branch(), parsed.head(), parsed.detached(), parsed.upstream(), parsed.ahead(),
                parsed.behind(), RepositoryOperations.detect(gitDir), stashCount(repo), stashTop(repo), parsed.changes());
    }

    /** Diff of one path: {@code staged} = index vs HEAD, otherwise worktree vs index (untracked files shown as additions). */
    public GitDiff.FileDiff diff(Path repo, String path, boolean staged) {
        if (!staged && !isTracked(repo, path)) {
            return untrackedDiff(repo, path);
        }
        var file = trackedDiff(repo, staged, path);
        if (!staged || file.change() != ChangeType.ADDED) {
            return file;
        }
        return stagedRenameSource(repo, path).map(oldPath -> trackedDiff(repo, true, oldPath, path)).orElse(file);
    }

    public List<Remote> remotes(Path repo) {
        var byName = new LinkedHashMap<String, String[]>();
        git.out(repo, "remote", "-v").lines().forEach(line -> collectRemote(byName, line));
        return byName.entrySet().stream()
                .map(entry -> new Remote(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    public Identity identity(Path repo) {
        return new Identity(config(repo, "user.name"), config(repo, "user.email"));
    }

    private FileDiff trackedDiff(Path repo, boolean staged, String... paths) {
        var args = new ArrayList<>(List.of("--literal-pathspecs", "diff", "--no-color", "--no-ext-diff", "-M"));
        if (staged) {
            args.add("--cached");
        }
        args.add("--");
        args.addAll(List.of(paths));
        return firstFile(git.out(repo, args.toArray(String[]::new)), paths[paths.length - 1], ChangeType.MODIFIED);
    }

    /** A path-limited diff cannot see a rename, so the source is looked up in the whole staged name-status list. */
    private Optional<String> stagedRenameSource(Path repo, String path) {
        var fields = git.out(repo, "diff", "--cached", "-M", "--name-status", "-z").split("\0");
        var i = 0;
        while (i < fields.length) {
            var renamed = fields[i].startsWith("R");
            if (renamed && i + 2 < fields.length && fields[i + 2].equals(path)) {
                return Optional.of(fields[i + 1]);
            }
            i += renamed || fields[i].startsWith("C") ? 3 : 2;
        }
        return Optional.empty();
    }

    private static void collectRemote(LinkedHashMap<String, String[]> byName, String line) {
        var tab = line.indexOf('\t');
        var space = line.lastIndexOf(' ');
        if (tab < 0 || space < tab) {
            return;
        }
        var urls = byName.computeIfAbsent(line.substring(0, tab), name -> new String[2]);
        var url = line.substring(tab + 1, space);
        urls[line.endsWith("(push)") ? 1 : 0] = url;
        if (urls[1] == null) {
            urls[1] = url;
        }
    }

    private String config(Path repo, String key) {
        var result = git.tryRun(repo, "config", key);
        var value = result.stdout().strip();
        return result.ok() && !value.isEmpty() ? value : null;
    }

    private Path gitDirectory(Path repo) {
        var result = git.tryRun(repo, "rev-parse", "--git-dir");
        return result.ok() ? repo.resolve(result.stdout().strip()) : null;
    }

    private String stashTop(Path repo) {
        var result = git.tryRun(repo, "rev-parse", "--verify", "--quiet", STASH_REF);
        return result.ok() ? result.stdout().strip() : null;
    }

    private int stashCount(Path repo) {
        var result = git.tryRun(repo, "rev-list", "--walk-reflogs", "--count", STASH_REF);
        return result.ok() ? Integer.parseInt(result.stdout().strip()) : 0;
    }

    private boolean isTracked(Path repo, String path) {
        return git.tryRun(repo, "--literal-pathspecs", "ls-files", "--error-unmatch", "--", path).ok();
    }

    private FileDiff untrackedDiff(Path repo, String path) {
        if (tooLarge(repo.resolve(path))) {
            var marker = new Line(LineKind.META, TOO_LARGE_MARKER, null, null);
            return new FileDiff(path, null, ChangeType.UNTRACKED, 0, 0, false, List.of(marker));
        }
        var result = git.tryRun(repo, "diff", "--no-index", "--no-color", "--no-ext-diff", "--", NULL_DEVICE, path);
        return firstFile(result.stdout(), path, ChangeType.UNTRACKED);
    }

    private boolean tooLarge(Path file) {
        try {
            return Files.size(file) > git.properties().maxUntrackedBytes();
        } catch (IOException e) {
            return false;
        }
    }

    private FileDiff firstFile(String diff, String path, ChangeType emptyChange) {
        var files = UnifiedDiffParser.parse(diff, git.properties().maxDiffLines(), git.properties().maxDiffLines(), true);
        if (files.isEmpty()) {
            return new FileDiff(path, null, emptyChange, 0, 0, false, List.of());
        }
        var file = files.getFirst();
        return emptyChange == ChangeType.UNTRACKED
                ? new FileDiff(path, null, ChangeType.UNTRACKED, file.additions(), file.deletions(), file.binary(), file.lines(), file.fileMode())
                : file;
    }
}
