package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Staging area and commits. All methods throw {@link GitException}; {@code repo} is the repository root. */
@Service
public class GitIndexService {

    static final int MAX_PATHS_ON_COMMAND_LINE = 100;

    private static final String GIT_DIRECTORY = ".git";
    private static final char REPLACEMENT_CHARACTER = '�';
    private static final String LITERAL_PATHSPECS = "--literal-pathspecs";
    private static final List<String> PATHS_FROM_STDIN = List.of("--pathspec-from-file=-", "--pathspec-file-nul");
    private static final List<String> COMMIT_HOOKS = List.of("hooks/pre-commit", "hooks/commit-msg");

    private final GitRunner git;

    @Autowired
    public GitIndexService(GitRunner git) {
        this.git = git;
    }

    public void stage(Path repo, List<String> paths) {
        withPaths(repo, paths, List.of("add"));
    }

    public void stageAll(Path repo) {
        git.out(repo, "add", "-A");
    }

    public void unstage(Path repo, List<String> paths) {
        withPaths(repo, paths, hasCommits(repo) ? List.of("restore", "--staged") : List.of("rm", "--cached", "-r", "-f", "-q"));
    }

    /** Throws away worktree changes of the paths (tracked: restore from index; untracked: delete). Irreversible. */
    public void discard(Path repo, List<String> paths) {
        var valid = RepositoryPaths.validate(paths);
        var tracked = trackedFiles(repo, valid);
        var untracked = valid.stream().filter(path -> !isTracked(tracked, path)).toList();
        untracked.forEach(path -> delete(RepositoryPaths.resolveInside(repo, path)));
        withPaths(repo, valid.stream().filter(path -> isTracked(tracked, path)).toList(), List.of("restore", "--worktree"));
    }

    /** Applies a unified patch to the index only (hunk/line staging); {@code reverse} unstages it. */
    public void applyToIndex(Path repo, String patch, boolean reverse) {
        if (patch.indexOf(REPLACEMENT_CHARACTER) >= 0) {
            throw new GitException(Kind.OTHER, "This file is not valid UTF-8, so its hunks cannot be staged; stage the whole file instead", "");
        }
        var args = new ArrayList<>(List.of("apply", "--cached"));
        if (reverse) {
            args.add("--reverse");
        }
        args.addAll(List.of("--recount", "--whitespace=nowarn", "-"));
        git.outWithInput(repo, patch.getBytes(StandardCharsets.UTF_8), args.toArray(String[]::new));
    }

    /** @return the new commit's short hash. Fails with NOTHING_TO_COMMIT / HOOK_FAILED / IDENTITY_UNKNOWN / ... */
    public String commit(Path repo, CommitRequest request) {
        var message = request.message() == null ? "" : request.message();
        if (message.isBlank() && !request.amend()) {
            throw new GitException(Kind.OTHER, "The commit message is empty", "");
        }
        try {
            git.outWithInput(repo, message.getBytes(StandardCharsets.UTF_8), commitArguments(request, message).toArray(String[]::new));
        } catch (GitException e) {
            throw request.skipHooks() ? e : asHookFailure(repo, e);
        }
        return git.out(repo, "rev-parse", "--short", "HEAD").strip();
    }

    private static List<String> commitArguments(CommitRequest request, String message) {
        var args = new ArrayList<>(List.of("commit"));
        args.addAll(message.isBlank() ? List.of("--no-edit") : List.of("-F", "-"));
        if (request.amend()) {
            args.add("--amend");
        }
        if (request.signOff()) {
            args.add("--signoff");
        }
        if (request.skipHooks()) {
            args.add("--no-verify");
        }
        return args;
    }

    /** git prints nothing of its own when a hook rejects the commit, so an unexplained failure with an active hook is the hook's. */
    private GitException asHookFailure(Path repo, GitException failure) {
        var activeHook = failure.kind() == Kind.OTHER && COMMIT_HOOKS.stream().anyMatch(hook -> hookExists(repo, hook));
        return activeHook ? new GitException(Kind.HOOK_FAILED, failure.getMessage(), failure.output()) : failure;
    }

    private boolean hookExists(Path repo, String hook) {
        var location = git.tryRun(repo, "rev-parse", "--git-path", hook).stdout().strip();
        return !location.isEmpty() && Files.exists(repo.resolve(location));
    }

    private boolean hasCommits(Path repo) {
        return git.tryRun(repo, "rev-parse", "--verify", "--quiet", "HEAD").ok();
    }

    private Set<String> trackedFiles(Path repo, List<String> paths) {
        var tracked = new HashSet<String>();
        for (var start = 0; start < paths.size(); start += MAX_PATHS_ON_COMMAND_LINE) {
            var chunk = paths.subList(start, Math.min(paths.size(), start + MAX_PATHS_ON_COMMAND_LINE));
            var args = Stream.concat(Stream.of(LITERAL_PATHSPECS, "ls-files", "-z", "--"), chunk.stream());
            Stream.of(git.out(repo, args.toArray(String[]::new)).split("\0")).filter(name -> !name.isEmpty()).forEach(tracked::add);
        }
        return tracked;
    }

    private static boolean isTracked(Set<String> tracked, String path) {
        return tracked.contains(path) || tracked.stream().anyMatch(name -> name.startsWith(path + "/"));
    }

    private void withPaths(Path repo, List<String> paths, List<String> command) {
        if (!paths.isEmpty()) {
            runWithPaths(repo, RepositoryPaths.validate(paths), command);
        }
    }

    private String runWithPaths(Path repo, List<String> paths, List<String> command) {
        var args = new ArrayList<String>();
        args.add(LITERAL_PATHSPECS);
        args.addAll(command);
        if (paths.size() <= MAX_PATHS_ON_COMMAND_LINE) {
            args.add("--");
            args.addAll(paths);
            return git.out(repo, args.toArray(String[]::new));
        }
        args.addAll(PATHS_FROM_STDIN);
        var nulSeparated = String.join("\0", paths) + "\0";
        return git.outWithInput(repo, nulSeparated.getBytes(StandardCharsets.UTF_8), args.toArray(String[]::new));
    }

    private static void delete(Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && Files.exists(target.resolve(GIT_DIRECTORY), LinkOption.NOFOLLOW_LINKS)) {
            throw new GitException(Kind.OTHER, "'" + target.getFileName() + "' is a nested repository; delete it by hand", "");
        }
        try {
            TreeDeleter.delete(target);
        } catch (IOException e) {
            throw new GitException(Kind.OTHER, "Cannot delete " + target + ": " + e.getMessage(), "");
        }
    }
}
