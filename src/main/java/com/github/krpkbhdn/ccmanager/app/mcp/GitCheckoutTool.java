package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitBranchService;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitCheckoutTool extends GitTool {

    private final GitBranchService branches;

    GitCheckoutTool(ProjectService projects, GitStatusService status, GitBranchService branches, JsonMapper mapper) {
        super(projects, status, mapper);
        this.branches = branches;
    }

    @Override
    public String name() {
        return "git_checkout";
    }

    @Override
    public String description() {
        return "Switches a project to a branch; with create=true makes a new branch from the current HEAD and switches to it. "
                + "Fails when local changes would be overwritten.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + ","
                + "\"branch\":{\"type\":\"string\"},"
                + "\"create\":{\"type\":\"boolean\",\"description\":\"Create the branch first\"}},"
                + "\"required\":[\"project\",\"branch\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        var branch = requiredText(arguments, "branch");
        if (flag(arguments, "create")) {
            branches.create(repo, branch, null, true);
            return "Created and switched to branch '" + branch + "'";
        }
        branches.checkout(repo, branch, false);
        return "Switched to branch '" + branch + "'";
    }
}
