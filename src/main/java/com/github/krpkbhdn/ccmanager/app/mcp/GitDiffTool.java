package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitStatus;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitDiffTool extends GitTool {

    static final int MAX_CHARS = 20_000;
    private static final String GIT_DIR = ".git";

    private final GitStatusService status;

    GitDiffTool(ProjectService projects, GitStatusService status, JsonMapper mapper) {
        super(projects, status, mapper);
        this.status = status;
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
        var text = new StringBuilder();
        for (var changed : paths) {
            Stream.of(true, false).map(staged -> status.diff(repo, changed, staged)).forEach(diff -> append(text, diff));
            if (text.length() > MAX_CHARS) {
                break;
            }
        }
        if (text.isEmpty()) {
            return "No changes";
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "\n... truncated" : text.toString();
    }

    /** The .git directory is refused too: its config holds remote URLs and tokens. An untracked path is read straight from disk by git diff --no-index, so it must stay inside the repository, symlinks included. */
    private static void requireInside(Path repo, String path) {
        try {
            var root = Files.exists(repo) ? repo.toRealPath() : repo.normalize();
            var target = repo.resolve(path).normalize();
            var real = Files.exists(target) ? target.toRealPath() : target;
            var gitDir = repo.normalize().resolve(GIT_DIR);
            if (!target.startsWith(repo.normalize()) || !real.startsWith(root) || target.startsWith(gitDir)) {
                throw new IllegalArgumentException("Path is outside the repository or inside .git: " + path);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve path: " + path);
        }
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
