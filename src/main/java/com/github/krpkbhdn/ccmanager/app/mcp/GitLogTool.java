package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitLog;
import com.github.krpkbhdn.ccmanager.git.GitLogService;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitLogTool extends GitTool {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final GitLogService log;

    GitLogTool(ProjectService projects, GitStatusService status, GitLogService log, JsonMapper mapper) {
        super(projects, status, mapper);
        this.log = log;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "git_log";
    }

    @Override
    public String description() {
        return "Lists the most recent commits of the current branch of a project (hash, author, date, subject), newest first.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY
                + ",\"limit\":{\"type\":\"integer\",\"description\":\"Commits to show, default " + DEFAULT_LIMIT + ", max " + MAX_LIMIT + "\"}"
                + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        var limit = Math.clamp(arguments.path("limit").asInt(DEFAULT_LIMIT), 1, MAX_LIMIT);
        var page = log.log(repo, new GitLog.Query("HEAD", null, null, null, 0, limit));
        return json(page.rows().stream().map(row -> commit(row.commit())).toList());
    }

    private static LinkedHashMap<String, Object> commit(GitLog.Commit commit) {
        var result = new LinkedHashMap<String, Object>();
        result.put("hash", commit.shortHash());
        result.put("author", commit.authorName());
        result.put("date", String.valueOf(commit.authorDate()));
        result.put("subject", commit.subject());
        return result;
    }
}
