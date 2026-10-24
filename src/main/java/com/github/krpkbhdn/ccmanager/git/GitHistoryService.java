package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.ResetMode;
import com.github.krpkbhdn.ccmanager.git.GitMisc.Tag;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/** Actions on commits and tags. All methods throw {@link GitException}. */
@Service
public class GitHistoryService {

    public void reset(Path repo, String commitish, ResetMode mode) { throw todo(); }

    /** Creates a revert commit. Conflicts leave the repo in the reverting state. */
    public void revert(Path repo, String commitish) { throw todo(); }

    public void cherryPick(Path repo, String commitish) { throw todo(); }

    public List<Tag> tags(Path repo) { throw todo(); }

    /** Annotated tag when {@code message} is non-blank, lightweight otherwise; {@code commitish} null = HEAD. */
    public void createTag(Path repo, String name, String commitish, String message) { throw todo(); }

    public void deleteTag(Path repo, String name) { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
