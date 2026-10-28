package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.app.AssistantTextLimitProperties;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GetPromptToolTest {

    private final PromptTemplateService prompts = mock(PromptTemplateService.class);
    private final GetPromptTool tool = new GetPromptTool(prompts, new AssistantTextLimitProperties(20));

    @Test
    void returnsFullText() {
        when(prompts.find(1)).thenReturn(Optional.of(new PromptTemplate(1, "Fix", "d", "Do the thing", null, null)));

        assertThat(tool.call(JsonMapper.shared().readTree("{\"id\":1}"))).contains("Do the thing");
    }

    @Test
    void cutsALongTextAndSaysSo() {
        when(prompts.find(3)).thenReturn(Optional.of(new PromptTemplate(3, "Long", "d", "x".repeat(50), null, null)));

        var result = tool.call(JsonMapper.shared().readTree("{\"id\":3}"));

        assertThat(result).contains("x".repeat(20)).doesNotContain("x".repeat(21)).contains("truncated", "20 of 50");
    }

    @Test
    void rejectsUnknownPrompt() {
        when(prompts.find(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"id\":2}"))).hasMessageContaining("2");
    }
}
