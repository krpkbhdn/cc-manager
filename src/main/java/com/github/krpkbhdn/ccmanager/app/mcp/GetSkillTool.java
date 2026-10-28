package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AssistantTextLimitProperties;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class GetSkillTool extends ExtensionScopeTool {

    private final SkillService skills;
    private final AssistantTextLimitProperties limits;

    GetSkillTool(ProjectService projects, SkillService skills, AssistantTextLimitProperties limits) {
        super(projects);
        this.skills = skills;
        this.limits = limits;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_skill";
    }

    @Override
    public String description() {
        return "Returns the SKILL.md of a skill (cut at a limit, and marked when cut); without scope the first skill of that name wins, plugin skills included.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + SkillTool.NAME_PROPERTY + "," + OPTIONAL_PROJECT_PROPERTY + ","
                + "\"scope\":{\"type\":\"string\",\"enum\":[\"user\",\"project\",\"local\"],\"description\":\"Only skills of this scope\"}},"
                + "\"required\":[\"name\"]}";
    }

    @Override
    protected String execute(JsonNode arguments) {
        var name = McpToolDescriptor.requiredText(arguments, "name");
        var scope = arguments.hasNonNull("scope") ? scopeOf(arguments) : null;
        return skills.list(projectDirectory(arguments)).stream()
                .filter(skill -> skill.name().equals(name) && (scope == null || skill.scope() == scope))
                .findFirst()
                .map(skill -> capped(skills.read(skill), limits.maxTextLength()))
                .orElseThrow(() -> new IllegalArgumentException("No skill named " + name + ", see list_skills"));
    }
}
