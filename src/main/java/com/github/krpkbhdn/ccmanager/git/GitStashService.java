package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.Stash;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiffService;
import com.github.krpkbhdn.ccmanager.workspace.UnifiedDiffParser;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Stash entries by index (stash@{n}). A conflicting apply/pop throws CONFLICT and git keeps the entry. */
@Service
public class GitStashService {

    private static final String LIST_FORMAT = "--format=%x1e%gd%x1f%gs%x1f%cI";
    private static final Pattern INDEX = Pattern.compile("stash@\\{(\\d+)}");
    private static final Pattern SUBJECT = Pattern.compile("^(?:WIP on|On) ([^:]+): (.*)$", Pattern.DOTALL);
    private static final String NOTHING_TO_STASH = "No local changes to save";
    private static final int FIELD_COUNT = 3;

    private final GitRunner git;

    @Autowired
    public GitStashService(GitRunner git) {
        this.git = git;
    }

    public List<Stash> list(Path repo) {
        var stashes = new ArrayList<Stash>();
        for (var record : git.out(repo, "stash", "list", LIST_FORMAT).split("\u001e")) {
            if (!record.isBlank()) {
                stashes.add(parse(record.split("\u001f", FIELD_COUNT)));
            }
        }
        return List.copyOf(stashes);
    }

    private static Stash parse(String[] fields) {
        var index = INDEX.matcher(fields[0]);
        var subject = SUBJECT.matcher(fields[1]);
        var found = subject.matches();
        return new Stash(index.matches() ? Integer.parseInt(index.group(1)) : 0, found ? subject.group(2) : fields[1],
                found ? subject.group(1) : null, OffsetDateTime.parse(fields[2].strip()).toInstant());
    }

    public void push(Path repo, String message, boolean includeUntracked) {
        var args = new ArrayList<>(List.of("stash", "push"));
        if (includeUntracked) {
            args.add("--include-untracked");
        }
        if (message != null && !message.isBlank()) {
            args.addAll(List.of("-m", message));
        }
        if (git.out(repo, args.toArray(String[]::new)).contains(NOTHING_TO_STASH)) {
            throw new GitException(Kind.NOTHING_TO_COMMIT, NOTHING_TO_STASH, "");
        }
    }

    public void apply(Path repo, int index) {
        git.out(repo, "stash", "apply", ref(index));
    }

    public void pop(Path repo, int index) {
        git.out(repo, "stash", "pop", ref(index));
    }

    public void drop(Path repo, int index) {
        git.out(repo, "stash", "drop", ref(index));
    }

    /** Tracked changes of the entry, plus its untracked files (stored as the entry's third parent) as UNTRACKED. */
    public GitDiff show(Path repo, int index) {
        var ref = ref(index);
        var files = new ArrayList<>(parseDiff(git.out(repo, "diff", "--no-color", "--no-ext-diff", "-M", ref + "^1", ref)));
        if (git.tryRun(repo, "rev-parse", "--verify", "--quiet", ref + "^3").ok()) {
            parseDiff(git.out(repo, "show", "--format=", "--no-color", "--no-ext-diff", "--root", ref + "^3")).stream()
                    .map(GitStashService::asUntracked)
                    .forEach(files::add);
        }
        return new GitDiff(true, List.copyOf(files));
    }

    private static List<FileDiff> parseDiff(String diff) {
        return UnifiedDiffParser.parse(diff, GitDiffService.MAX_LINES_PER_FILE, GitDiffService.MAX_TOTAL_LINES);
    }

    private static FileDiff asUntracked(FileDiff file) {
        return new FileDiff(file.path(), file.oldPath(), ChangeType.UNTRACKED, file.additions(), file.deletions(), file.binary(),
                file.lines());
    }

    private static String ref(int index) {
        return "stash@{" + index + "}";
    }
}
