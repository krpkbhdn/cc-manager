package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeletePromptToolTest {

    private final PromptTemplateService prompts = mock(PromptTemplateService.class);
    private final DeletePromptTool tool = new DeletePromptTool(prompts);

    @Test
    void deletesAndAsksForApproval() {
        when(prompts.find(1)).thenReturn(Optional.of(new PromptTemplate(1, "Fix", "", "Go", null, null)));

        assertThat(tool.call(JsonMapper.shared().readTree("{\"id\":1}"))).contains("Fix");

        verify(prompts).delete(1);
        assertThat(tool.autoApproved()).isFalse();
    }

    @Test
    void rejectsUnknownPrompt() {
        when(prompts.find(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"id\":2}"))).hasMessageContaining("2");
    }
}
