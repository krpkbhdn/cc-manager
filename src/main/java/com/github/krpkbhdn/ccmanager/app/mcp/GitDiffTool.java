package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitStatus;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.git.SecretFiles;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitDiffTool extends GitTool {

    static final int MAX_CHARS = 20_000;
    static final int MAX_FILES = 200;
    private static final String GIT_DIR = ".git";
    private static final String GIT_SHORT_NAME = "GIT~1";
    private static final Pattern TRAILING_DOTS_AND_SPACES = Pattern.compile("[. ]+$");

    GitDiffTool(ProjectService projects, GitStatusService status, JsonMapper mapper) {
        super(projects, status, mapper);
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "git_diff";
    }

    @Override
    public String description() {
        return "Shows the uncommitted changes (staged and unstaged) of a project as a compact diff, optionally of one path; long output is truncated.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY
                + ",\"path\":{\"type\":\"string\",\"description\":\"File path relative to the repository; all changed files when omitted\"}"
                + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        var path = optionalText(arguments, "path");
        if (!path.isEmpty()) {
            requireInside(repo, path);
        }
        var paths = path.isEmpty() ? status.status(repo).changes().stream().map(GitStatus.Change::path).toList() : List.of(path);
        var tooManyFiles = paths.size() > MAX_FILES;
        var text = new StringBuilder();
        for (var changed : paths.subList(0, Math.min(paths.size(), MAX_FILES))) {
            if (looksSecret(changed)) {
                text.append("--- ").append(changed).append(" (content omitted: looks like a secret file)\n");
                continue;
            }
            Stream.of(true, false).map(staged -> status.diff(repo, changed, staged)).forEach(diff -> append(text, diff));
            if (text.length() > MAX_CHARS) {
                break;
            }
        }
        if (text.isEmpty() && !tooManyFiles) {
            return "No changes";
        }
        if (text.length() > MAX_CHARS) {
            return text.substring(0, MAX_CHARS) + "\n... truncated";
        }
        return tooManyFiles ? text + "... truncated: only the first " + MAX_FILES + " of " + paths.size() + " changed files were diffed\n" : text.toString();
    }

    /** The .git directory is refused too: its config holds remote URLs and tokens. An untracked path is read straight from disk by git diff --no-index, so it must stay inside the repository, symlinks included. */
    static void requireInside(Path repo, String path) {
        try {
            var root = Files.exists(repo) ? repo.toRealPath() : repo.normalize();
            var target = repo.resolve(path).normalize();
            var real = Files.exists(target) ? target.toRealPath() : target;
            if (!target.startsWith(repo.normalize()) || !real.startsWith(root)
                    || hasGitSegment(repo.normalize().relativize(target)) || hasGitSegment(root.relativize(real))) {
                throw new IllegalArgumentException("Path is outside the repository or inside .git: " + path);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve path: " + path);
        }
    }

    /** Checks the name the way Windows resolves it: no trailing dots, spaces or slashes, no "." or ".." segments. */
    static boolean looksSecret(String path) {
        var resolved = new java.util.ArrayDeque<String>();
        for (var segment : path.split("[/\\\\]")) {
            var name = TRAILING_DOTS_AND_SPACES.matcher(segment).replaceFirst("");
            if (segment.equals("..")) {
                resolved.pollLast();
            } else if (!name.isEmpty()) {
                resolved.add(name);
            }
        }
        return SecretFiles.looksSecret(String.join("/", resolved));
    }

    /** Windows drops trailing dots and spaces and also serves .git under its 8.3 name. */
    private static boolean hasGitSegment(Path relative) {
        return StreamSupport.stream(relative.spliterator(), false).map(segment -> TRAILING_DOTS_AND_SPACES.matcher(segment.toString()).replaceFirst(""))
                .anyMatch(name -> name.equalsIgnoreCase(GIT_DIR) || name.equalsIgnoreCase(GIT_SHORT_NAME));
    }

    private static void append(StringBuilder text, GitDiff.FileDiff diff) {
        if (diff.lines().isEmpty() && !diff.binary()) {
            return;
        }
        text.append("--- ").append(diff.path()).append(" (").append(diff.change()).append(" +").append(diff.additions())
                .append(" -").append(diff.deletions()).append(")\n");
        if (diff.binary()) {
            text.append("binary file\n");
        }
        diff.lines().stream().filter(line -> line.kind() != GitDiff.LineKind.META)
                .forEach(line -> text.append(prefix(line.kind())).append(line.text()).append('\n'));
    }

    private static String prefix(GitDiff.LineKind kind) {
        return switch (kind) {
            case ADDED -> "+";
            case REMOVED -> "-";
            case CONTEXT -> " ";
            default -> "";
        };
    }
}
