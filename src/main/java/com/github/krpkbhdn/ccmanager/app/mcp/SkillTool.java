package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.skill.Skill;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Base of the skill tools: finds a skill by its {@code name}, the folder name, among those visible to the optional project. */
abstract class SkillTool extends ExtensionScopeTool {

    static final String NAME_PROPERTY = "\"name\":{\"type\":\"string\",\"description\":\"Skill name from list_skills\"}";

    final SkillService skills;

    SkillTool(ProjectService projects, SkillService skills) {
        super(projects);
        this.skills = skills;
    }

    /** Own (non-plugin) skill of the requested scope; the folder name is what set_skill creates, so it is the key. */
    final Optional<Skill> findOwn(JsonNode arguments) {
        var name = McpToolDescriptor.requiredText(arguments, "name");
        var scope = scopeOf(arguments);
        var matches = skills.list(projectDirectory(arguments)).stream()
                .filter(skill -> !skill.readOnly() && skill.scope() == scope && skill.directory().getFileName().toString().equals(name))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("More than one skill folder is named " + name + ", see list_skills");
        }
        return matches.stream().findFirst();
    }
}
