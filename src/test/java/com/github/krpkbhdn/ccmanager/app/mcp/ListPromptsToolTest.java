package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ListPromptsToolTest {

    private final PromptTemplateService prompts = mock(PromptTemplateService.class);
    private final ListPromptsTool tool = new ListPromptsTool(prompts);

    @Test
    void listsWithoutTextAndFiltersByProject() {
        when(prompts.listFor(3)).thenReturn(List.of(new PromptTemplate(1, "Fix", "d", "SECRET TEXT", 3L, null)));

        var text = tool.call(JsonMapper.shared().readTree("{\"project\":3}"));

        assertThat(text).contains("\"name\":\"Fix\"").contains("\"projectId\":3").doesNotContain("SECRET TEXT");
    }

    @Test
    void listsAllWithoutProject() {
        when(prompts.list()).thenReturn(List.of());

        assertThat(tool.call(JsonMapper.shared().createObjectNode())).isEqualTo("[]");
    }
}
