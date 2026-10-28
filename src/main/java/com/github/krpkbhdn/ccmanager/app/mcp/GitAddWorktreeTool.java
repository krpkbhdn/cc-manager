package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitAddWorktreeTool extends GitTool {

    private static final String DIRECTORY_INFIX = "-wt-";

    private final GitWorktreeService worktrees;

    GitAddWorktreeTool(ProjectService projects, GitStatusService status, GitWorktreeService worktrees, JsonMapper mapper) {
        super(projects, status, mapper);
        this.worktrees = worktrees;
    }

    @Override
    public String name() {
        return "git_add_worktree";
    }

    @Override
    public String description() {
        return "Creates a new branch from the current HEAD in a new git worktree. Without path the folder is a sibling of the repository named <repo>-wt-<branch>.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + ","
                + "\"branch\":{\"type\":\"string\",\"description\":\"New branch name\"},"
                + "\"path\":{\"type\":\"string\",\"description\":\"Absolute folder of the worktree (optional)\"}},"
                + "\"required\":[\"project\",\"branch\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        var branch = requiredText(arguments, "branch");
        var path = optionalText(arguments, "path");
        var target = path.isEmpty()
                ? repo.resolveSibling(repo.getFileName() + DIRECTORY_INFIX + branch.replace('/', '-'))
                : Path.of(path);
        worktrees.add(repo, branch, null, target);
        return "Created worktree " + target + " on new branch '" + branch + "'";
    }
}
