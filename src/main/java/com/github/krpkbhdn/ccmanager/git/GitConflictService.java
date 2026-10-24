package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** Finishing or abandoning a stopped merge/rebase/cherry-pick/revert (see {@link GitStatus#operation()}). */
@Service
public class GitConflictService {

    /** Takes one side of a conflicted file wholesale and stages it. */
    public void resolve(Path repo, String path, ConflictSide side) { throw todo(); }

    /** The file was edited by hand (or in the IDE): stage it as resolved. */
    public void markResolved(Path repo, String path) { throw todo(); }

    /** merge --continue / rebase --continue / cherry-pick --continue / revert --continue, whichever is in progress. */
    public void continueOperation(Path repo) { throw todo(); }

    public void abortOperation(Path repo) { throw todo(); }

    /** Rebase only: skip the current commit. */
    public void skipCommit(Path repo) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
