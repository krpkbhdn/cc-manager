package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/** Staging area and commits. All methods throw {@link GitException}. */
@Service
public class GitIndexService {

    public void stage(Path repo, List<String> paths) { throw todo(); }

    public void stageAll(Path repo) { throw todo(); }

    public void unstage(Path repo, List<String> paths) { throw todo(); }

    /** Throws away worktree changes of the paths (tracked: restore from index; untracked: delete). Irreversible. */
    public void discard(Path repo, List<String> paths) { throw todo(); }

    /** Applies a unified patch to the index only (hunk/line staging); {@code reverse} unstages it. */
    public void applyToIndex(Path repo, String patch, boolean reverse) { throw todo(); }

    /** @return the new commit's short hash. Fails with NOTHING_TO_COMMIT / HOOK_FAILED / ... */
    public String commit(Path repo, CommitRequest request) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
