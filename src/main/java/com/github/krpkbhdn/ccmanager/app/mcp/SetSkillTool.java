package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.skill.Skill;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SetSkillTool extends SkillTool {

    SetSkillTool(ProjectService projects, SkillService skills) {
        super(projects, skills);
    }

    private void writeOrDiscard(Skill created, String content) {
        try {
            skills.write(created, content);
        } catch (RuntimeException e) {
            skills.delete(created);
            throw e;
        }
    }

    @Override
    public String name() {
        return "set_skill";
    }

    @Override
    public String description() {
        return "Creates a user or project skill, or replaces the SKILL.md of an existing one; description is needed only to create.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + NAME_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + "," + SCOPE_PROPERTY + ","
                + "\"description\":{\"type\":\"string\",\"description\":\"One-line summary, required when the skill does not exist yet\"},"
                + "\"content\":{\"type\":\"string\",\"description\":\"Complete SKILL.md including the front matter\"}},"
                + "\"required\":[\"name\",\"content\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var content = McpToolDescriptor.requiredText(arguments, "content");
        var existing = findOwn(arguments);
        if (existing.isPresent()) {
            skills.write(existing.get(), content);
            return "Skill " + existing.get().name() + " updated.";
        }
        var name = McpToolDescriptor.requiredText(arguments, "name");
        var description = McpToolDescriptor.requiredText(arguments, "description");
        var created = skills.create(scopeOf(arguments), projectDirectory(arguments), name, description);
        writeOrDiscard(created, content);
        return "Skill " + name + " created in " + created.scope().cliValue() + " scope.";
    }
}
