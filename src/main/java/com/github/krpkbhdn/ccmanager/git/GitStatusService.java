package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.Identity;
import com.github.krpkbhdn.ccmanager.git.GitMisc.Remote;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only repository state for the Changes view. Never throws for "not a repository": it returns {@link GitStatus#notARepository()}. */
@Service
public class GitStatusService {

    public GitStatus status(Path repo) { throw todo(); }

    /** Diff of one path: {@code staged} = index vs HEAD, otherwise worktree vs index (untracked files shown as additions). */
    public GitDiff.FileDiff diff(Path repo, String path, boolean staged) { throw todo(); }

    public List<Remote> remotes(Path repo) { throw todo(); }

    public Identity identity(Path repo) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
