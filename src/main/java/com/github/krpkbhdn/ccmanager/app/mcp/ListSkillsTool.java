package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListSkillsTool extends ExtensionScopeTool {

    private final SkillService skills;

    ListSkillsTool(ProjectService projects, SkillService skills) {
        super(projects);
        this.skills = skills;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_skills";
    }

    @Override
    public String description() {
        return "Lists the user's skills, plus the project's own and those of active plugins when a project is given.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + OPTIONAL_PROJECT_PROPERTY + "}}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        List<Map<String, Object>> entries = skills.list(projectDirectory(arguments)).stream()
                .map(skill -> Map.<String, Object>of("name", skill.name(), "description", skill.description(),
                        "folder", skill.directory().getFileName().toString(), "scope", skill.scope().cliValue(), "plugin", skill.pluginId(), "readOnly", skill.readOnly()))
                .toList();
        return json(entries);
    }
}
