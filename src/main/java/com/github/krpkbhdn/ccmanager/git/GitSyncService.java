package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode;
import com.github.krpkbhdn.ccmanager.git.GitMisc.PushOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Network operations; blocking (call off the UI thread). {@code progress} receives git's progress lines as they arrive (may be null). */
@Service
public class GitSyncService {

    private static final String DEFAULT_REMOTE = "origin";

    private final GitRunner git;
    private final BranchRefs refs;

    @Autowired
    public GitSyncService(GitRunner git) {
        this.git = git;
        this.refs = new BranchRefs(git);
    }

    /** git fetch --all --prune --tags. */
    public void fetch(Path repo, Consumer<String> progress) {
        network(repo, progress, "fetch", "--all", "--prune", "--tags", "--progress");
    }

    public void pull(Path repo, PullMode mode, Consumer<String> progress) {
        var args = new ArrayList<>(List.of("pull", "--progress"));
        args.addAll(switch (mode) {
            case MERGE -> List.of("--no-rebase", "--no-edit");
            case REBASE -> List.of("--rebase");
            case FAST_FORWARD_ONLY -> List.of("--ff-only");
        });
        network(repo, progress, args.toArray(String[]::new));
    }

    /** Pushes the current branch; fails with NO_UPSTREAM unless {@code setUpstream}, NON_FAST_FORWARD when rejected. */
    public void push(Path repo, PushOptions options, Consumer<String> progress) {
        var args = new ArrayList<>(List.of("push", "--progress"));
        if (options.forceWithLease()) {
            args.add("--force-with-lease");
        }
        if (options.tags()) {
            args.add("--tags");
        }
        if (options.setUpstream()) {
            var branch = refs.current(repo).orElseThrow(() -> new GitException(Kind.OTHER, "Cannot push a detached HEAD", ""));
            args.addAll(List.of("-u", remoteFor(repo, branch), branch));
        }
        network(repo, progress, args.toArray(String[]::new));
    }

    private void network(Path repo, Consumer<String> progress, String... args) {
        git.run(repo, GitRunner.SYNC_TIMEOUT, progress, null, Arrays.asList(args));
    }

    private String remoteFor(Path repo, String branch) {
        var configured = git.tryRun(repo, "config", "branch." + branch + ".remote");
        if (configured.ok() && !configured.stdout().isBlank()) {
            return configured.stdout().strip();
        }
        var remotes = git.out(repo, "remote").lines().map(String::strip).filter(name -> !name.isEmpty()).toList();
        if (remotes.size() == 1) {
            return remotes.getFirst();
        }
        if (remotes.contains(DEFAULT_REMOTE)) {
            return DEFAULT_REMOTE;
        }
        throw new GitException(Kind.NO_UPSTREAM, "No remote to push to; add one with `git remote add`", "");
    }
}
