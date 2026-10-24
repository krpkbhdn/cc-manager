package com.github.krpkbhdn.ccmanager.git;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Tells the UI when a repository changed under it (commits, checkouts, staging by Claude or an IDE): watches the .git
 * directory (HEAD, index, refs, merge/rebase state files) and the working tree, debounced.
 */
@Component
public class GitWatcher {

    /** @return unsubscribe handle; the callback runs on an arbitrary thread, at most a few times per second */
    public Runnable subscribe(Path repo, Runnable onChange) { throw new UnsupportedOperationException("TODO"); }
}
