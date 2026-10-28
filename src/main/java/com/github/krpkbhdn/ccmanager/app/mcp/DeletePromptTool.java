package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class DeletePromptTool extends AssistantTool {

    private final PromptTemplateService prompts;

    DeletePromptTool(PromptTemplateService prompts) {
        this.prompts = prompts;
    }

    @Override
    public String name() {
        return "delete_prompt";
    }

    @Override
    public String description() {
        return "Deletes a prompt template.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"Prompt id from list_prompts\"}},\"required\":[\"id\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "id");
        var prompt = prompts.find(id).orElseThrow(() -> new IllegalArgumentException("Prompt not found: " + id));
        prompts.delete(id);
        return "Prompt '%s' deleted.".formatted(prompt.name());
    }
}
