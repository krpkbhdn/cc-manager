package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitMisc.ResetMode;
import com.github.krpkbhdn.ccmanager.git.GitMisc.Tag;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Actions on commits and tags. All methods throw {@link GitException}. */
@Service
public class GitHistoryService {

    private static final String TAG_FORMAT = "--format=%1e%(refname:short)%1f%(objecttype)%1f%(*objectname)%1f%(objectname)%1f%(contents)";
    private static final String ANNOTATED_TYPE = "tag";
    private static final int FIELD_COUNT = 5;
    private static final String MAINLINE_PARENT = "1";

    private final GitRunner git;

    @Autowired
    public GitHistoryService(GitRunner git) {
        this.git = git;
    }

    public void reset(Path repo, String commitish, ResetMode mode) {
        git.out(repo, "reset", "--" + mode.name().toLowerCase(), GitRevisions.requireNotOption(commitish));
    }

    /** Creates a revert commit. Conflicts leave the repo in the reverting state. */
    public void revert(Path repo, String commitish) {
        git.out(repo, withMainline(repo, "revert", "--no-edit", commitish));
    }

    /** Merge commits are picked relative to their first parent. */
    public void cherryPick(Path repo, String commitish) {
        git.out(repo, withMainline(repo, "cherry-pick", commitish));
    }

    private String[] withMainline(Path repo, String... command) {
        var commitish = GitRevisions.requireNotOption(command[command.length - 1]);
        var args = new ArrayList<>(List.of(command).subList(0, command.length - 1));
        if (isMerge(repo, commitish)) {
            args.addAll(List.of("-m", MAINLINE_PARENT));
        }
        args.add(commitish);
        return args.toArray(String[]::new);
    }

    private boolean isMerge(Path repo, String commitish) {
        return git.tryRun(repo, "rev-parse", "--verify", "--quiet", commitish + "^2").ok();
    }

    public List<Tag> tags(Path repo) {
        var tags = new ArrayList<Tag>();
        for (var record : git.out(repo, "for-each-ref", "--sort=-creatordate", TAG_FORMAT, "refs/tags").split("\u001e")) {
            if (!record.isBlank()) {
                tags.add(parseTag(record.split("\u001f", FIELD_COUNT)));
            }
        }
        return List.copyOf(tags);
    }

    private static Tag parseTag(String[] fields) {
        var annotated = fields[1].equals(ANNOTATED_TYPE);
        return new Tag(fields[0], annotated ? fields[2] : fields[3], annotated ? fields[4].strip() : null, annotated);
    }

    /** Annotated tag when {@code message} is non-blank, lightweight otherwise; {@code commitish} null = HEAD. */
    public void createTag(Path repo, String name, String commitish, String message) {
        var args = new ArrayList<>(List.of("tag"));
        if (message != null && !message.isBlank()) {
            args.addAll(List.of("-a", "-m", message));
        }
        args.add(GitRevisions.requireNotOption(name));
        if (commitish != null) {
            args.add(GitRevisions.requireNotOption(commitish));
        }
        git.out(repo, args.toArray(String[]::new));
    }

    public void deleteTag(Path repo, String name) {
        git.out(repo, "tag", "-d", GitRevisions.requireNotOption(name));
    }
}
