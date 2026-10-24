package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode;
import com.github.krpkbhdn.ccmanager.git.GitMisc.PushOptions;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Network operations; blocking (call off the UI thread). {@code progress} receives git's progress lines as they arrive (may be null). */
@Service
public class GitSyncService {

    /** git fetch --all --prune --tags. */
    public void fetch(Path repo, Consumer<String> progress) { throw todo(); }

    public void pull(Path repo, PullMode mode, Consumer<String> progress) { throw todo(); }

    /** Pushes the current branch; fails with NO_UPSTREAM unless {@code setUpstream}, NON_FAST_FORWARD when rejected. */
    public void push(Path repo, PushOptions options, Consumer<String> progress) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
