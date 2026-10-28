package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SetPromptTool extends AssistantTool {

    private final PromptTemplateService prompts;

    SetPromptTool(PromptTemplateService prompts) {
        this.prompts = prompts;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "set_prompt";
    }

    @Override
    public String description() {
        return "Creates a prompt template, or with 'id' replaces an existing one; text may use {project}, {branch} and {file}.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"Prompt id to update, omit to create\"},"
                + "\"name\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"text\":{\"type\":\"string\"},"
                + "\"projectId\":{\"type\":\"integer\",\"description\":\"Bind to a project, omit for a global prompt\"},"
                + "\"defaultTeamId\":{\"type\":\"integer\",\"description\":\"Preferred team id from list_teams\"}},\"required\":[\"name\",\"text\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var name = McpToolDescriptor.requiredText(arguments, "name");
        var text = McpToolDescriptor.requiredText(arguments, "text");
        var description = arguments.path("description").asString("");
        var projectId = optionalId(arguments, "projectId");
        var teamId = optionalId(arguments, "defaultTeamId");
        var id = optionalId(arguments, "id");
        var prompt = id == null ? prompts.create(name, description, text, projectId, teamId) : prompts.update(id, name, description, text, projectId, teamId);
        return "Prompt '%s' (id %d) saved.".formatted(prompt.name(), prompt.id());
    }
}
