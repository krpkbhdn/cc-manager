package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListPromptsTool extends AssistantTool {

    private final PromptTemplateService prompts;

    ListPromptsTool(PromptTemplateService prompts) {
        this.prompts = prompts;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_prompts";
    }

    @Override
    public String description() {
        return "Lists the prompt templates (id, name, description, project and default team binding, no text); with 'project' only global ones and that project's.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"project\":{\"type\":\"integer\",\"description\":\"Project id, omit for all\"}}}";
    }

    @Override
    public String call(JsonNode arguments) {
        var project = optionalId(arguments, "project");
        var list = project == null ? prompts.list() : prompts.listFor(project);
        return json(list.stream().map(prompt -> {
            var result = new LinkedHashMap<String, Object>();
            result.put("id", prompt.id());
            result.put("name", prompt.name());
            result.put("description", prompt.description());
            result.put("projectId", prompt.projectId());
            result.put("defaultTeamId", prompt.defaultTeamId());
            return result;
        }).toList());
    }
}
