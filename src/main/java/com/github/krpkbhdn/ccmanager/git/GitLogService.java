package com.github.krpkbhdn.ccmanager.git;

import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** History with a computed lane layout (see {@link GitLog.Row}) and per-commit details. */
@Service
public class GitLogService {

    public GitLog.Page log(Path repo, GitLog.Query query) { throw todo(); }

    public GitLog.Details details(Path repo, String hash) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
