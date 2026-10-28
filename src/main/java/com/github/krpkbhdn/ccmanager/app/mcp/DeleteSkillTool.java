package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class DeleteSkillTool extends SkillTool {

    DeleteSkillTool(ProjectService projects, SkillService skills) {
        super(projects, skills);
    }

    @Override
    public String name() {
        return "delete_skill";
    }

    @Override
    public String description() {
        return "Deletes a user or project skill with its whole folder; plugin skills cannot be deleted.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + "," + SCOPE_PROPERTY + "},"
                + "\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var skill = findOwn(arguments).orElseThrow(() -> new IllegalArgumentException(
                "No user or project skill with that name in that scope, see list_skills"));
        skills.delete(skill);
        return "Skill " + skill.name() + " deleted.";
    }
}
