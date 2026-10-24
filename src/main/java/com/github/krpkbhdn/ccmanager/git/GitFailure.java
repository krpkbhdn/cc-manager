package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.util.List;

/** Maps git's (English, LC_ALL=C) error text to a {@link Kind}; first matching rule wins. */
final class GitFailure {

    private record Rule(Kind kind, List<String> markers) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Kind.NOT_A_REPOSITORY, List.of("not a git repository")),
            new Rule(Kind.LOCKED, List.of("index.lock", "another git process seems to be running")),
            new Rule(Kind.AUTH, List.of("Authentication failed", "could not read Username", "could not read Password",
                    "Permission denied (publickey", "terminal prompts disabled", "Host key verification failed",
                    "access denied or repository not exported", "Repository not found", "HTTP Basic: Access denied")),
            new Rule(Kind.CONFLICT, List.of("CONFLICT (", "Automatic merge failed", "fix conflicts and then commit",
                    "could not apply", "Resolve all conflicts manually")),
            new Rule(Kind.DIRTY_TREE, List.of("would be overwritten by", "Please commit your changes or stash them",
                    "cannot pull with rebase: You have unstaged changes", "cannot rebase: You have unstaged changes")),
            new Rule(Kind.NON_FAST_FORWARD, List.of("non-fast-forward", "Updates were rejected because",
                    "failed to push some refs", "fetch first", "Not possible to fast-forward")),
            new Rule(Kind.NO_UPSTREAM, List.of("has no upstream branch", "no tracking information",
                    "No configured push destination")),
            new Rule(Kind.HOOK_FAILED, List.of("hook declined", "hook failed", "pre-commit hook", "commit-msg hook")),
            new Rule(Kind.NOTHING_TO_COMMIT, List.of("nothing to commit", "no changes added to commit", "nothing added to commit")),
            new Rule(Kind.NOT_FOUND, List.of("unknown revision", "did not match any", "pathspec", "bad revision",
                    "not a valid object name", "invalid reference", "No such file or directory", "does not exist")));

    private GitFailure() {}

    static Kind classify(String output) {
        return RULES.stream()
                .filter(rule -> rule.markers().stream().anyMatch(output::contains))
                .map(Rule::kind)
                .findFirst()
                .orElse(Kind.OTHER);
    }

    /** First meaningful line of git's complaint, for toasts. */
    static String headline(String output) {
        return output.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("git failed");
    }
}
