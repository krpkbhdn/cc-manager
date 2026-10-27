package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.util.List;
import java.util.Optional;

/** Maps git's (English, LC_ALL=C) error text to a {@link Kind} and a user-readable message; first matching rule wins. */
final class GitFailure {

    static final String AUTH_HINT = "Git could not authenticate and cannot prompt here - run `git fetch` once in a terminal "
            + "or configure a credential helper / SSH agent";
    static final String IDENTITY_HINT = "Git does not know who you are - set user.name and user.email "
            + "(git config --global user.name \"Your Name\", git config --global user.email you@example.com)";

    private static final List<String> DIAGNOSTIC_PREFIXES = List.of("fatal: ", "error: ");

    private record Rule(Kind kind, List<String> markers) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Kind.NOT_A_REPOSITORY, List.of("not a git repository")),
            new Rule(Kind.LOCKED, List.of("index.lock", "another git process seems to be running")),
            new Rule(Kind.IDENTITY_UNKNOWN, List.of("Please tell me who you are", "unable to auto-detect email address",
                    "empty ident name")),
            new Rule(Kind.AUTH, List.of("Authentication failed", "could not read Username", "could not read Password",
                    "Permission denied (publickey", "terminal prompts disabled", "Host key verification failed",
                    "access denied or repository not exported", "Repository not found", "HTTP Basic: Access denied")),
            new Rule(Kind.CONFLICT, List.of("CONFLICT (", "Automatic merge failed", "fix conflicts and then commit",
                    "could not apply", "Resolve all conflicts manually")),
            new Rule(Kind.DIRTY_TREE, List.of("would be overwritten by", "Please commit your changes or stash them",
                    "cannot pull with rebase: You have unstaged changes", "cannot rebase: You have unstaged changes",
                    "contains modified or untracked files")),
            new Rule(Kind.HOOK_FAILED, List.of("hook declined", "hook failed", "pre-commit hook", "commit-msg hook")),
            new Rule(Kind.NON_FAST_FORWARD, List.of("non-fast-forward", "Updates were rejected because",
                    "failed to push some refs", "fetch first", "Not possible to fast-forward")),
            new Rule(Kind.NO_UPSTREAM, List.of("has no upstream branch", "no tracking information",
                    "No configured push destination")),
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

    /** Message for toasts: a fixed hint for failures the user must act on outside the app, otherwise git's own complaint. */
    static String message(Kind kind, String output) {
        return switch (kind) {
            case AUTH -> AUTH_HINT;
            case IDENTITY_UNKNOWN -> IDENTITY_HINT;
            default -> headline(output);
        };
    }

    /** The first fatal/error line, else the first line mentioning a conflict, else the first line. */
    static String headline(String output) {
        var lines = output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        return diagnostic(lines)
                .or(() -> lines.stream().filter(line -> line.contains("CONFLICT (")).findFirst())
                .or(() -> lines.stream().findFirst())
                .orElse("git failed");
    }

    private static Optional<String> diagnostic(List<String> lines) {
        return lines.stream()
                .flatMap(line -> DIAGNOSTIC_PREFIXES.stream().filter(line::startsWith).map(prefix -> line.substring(prefix.length())))
                .findFirst();
    }

    /** The git subcommand among global options such as --literal-pathspecs, for messages. */
    static String subcommand(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (arg.equals("-c")) {
                i++;
            } else if (!arg.startsWith("-")) {
                return arg;
            }
        }
        return "command";
    }
}
