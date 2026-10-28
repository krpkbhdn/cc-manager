package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AssistantTextLimitProperties;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class GetPromptTool extends AssistantTool {

    private final PromptTemplateService prompts;
    private final AssistantTextLimitProperties limits;

    GetPromptTool(PromptTemplateService prompts, AssistantTextLimitProperties limits) {
        this.prompts = prompts;
        this.limits = limits;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_prompt";
    }

    @Override
    public String description() {
        return "Returns one prompt template with its text (cut at a limit, and marked when cut).";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"Prompt id from list_prompts\"}},\"required\":[\"id\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "id");
        var prompt = prompts.find(id).orElseThrow(() -> new IllegalArgumentException("Prompt not found: " + id));
        return json(new PromptTemplate(prompt.id(), prompt.name(), prompt.description(), capped(prompt.text(), limits.maxTextLength()),
                prompt.projectId(), prompt.defaultTeamId()));
    }
}
