package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.Stash;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GitStashService {

    public List<Stash> list(Path repo) { throw todo(); }

    public void push(Path repo, String message, boolean includeUntracked) { throw todo(); }

    public void apply(Path repo, int index) { throw todo(); }

    public void pop(Path repo, int index) { throw todo(); }

    public void drop(Path repo, int index) { throw todo(); }

    public GitDiff show(Path repo, int index) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
