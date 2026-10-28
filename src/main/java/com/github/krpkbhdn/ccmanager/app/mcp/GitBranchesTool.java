package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitBranch;
import com.github.krpkbhdn.ccmanager.git.GitBranchService;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitBranchesTool extends GitTool {

    private final GitBranchService branches;

    GitBranchesTool(ProjectService projects, GitStatusService status, GitBranchService branches, JsonMapper mapper) {
        super(projects, status, mapper);
        this.branches = branches;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "git_branches";
    }

    @Override
    public String description() {
        return "Lists local and remote branches of a project with the current one, upstream and ahead/behind.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        return json(branches.branches(repo).stream().map(GitBranchesTool::branch).toList());
    }

    private static LinkedHashMap<String, Object> branch(GitBranch branch) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", branch.name());
        result.put("remote", branch.remote());
        result.put("current", branch.current());
        result.put("upstream", branch.upstream());
        result.put("ahead", branch.ahead());
        result.put("behind", branch.behind());
        return result;
    }
}
