package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService;
import com.github.krpkbhdn.ccmanager.git.Worktree;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitWorktreesTool extends GitTool {

    private final GitWorktreeService worktrees;

    GitWorktreesTool(ProjectService projects, GitStatusService status, GitWorktreeService worktrees, JsonMapper mapper) {
        super(projects, status, mapper);
        this.worktrees = worktrees;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "git_worktrees";
    }

    @Override
    public String description() {
        return "Lists the git worktrees of a project (main first) with branch, path, dirty and merged flags.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        return json(worktrees.list(repo).stream().map(GitWorktreesTool::worktree).toList());
    }

    private static LinkedHashMap<String, Object> worktree(Worktree worktree) {
        var result = new LinkedHashMap<String, Object>();
        result.put("path", worktree.path().toString());
        result.put("branch", worktree.branch());
        result.put("main", worktree.main());
        result.put("dirty", worktree.dirty());
        result.put("merged", worktree.merged());
        return result;
    }
}
