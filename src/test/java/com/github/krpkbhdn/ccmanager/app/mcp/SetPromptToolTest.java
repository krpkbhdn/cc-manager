package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SetPromptToolTest {

    private final PromptTemplateService prompts = mock(PromptTemplateService.class);
    private final SetPromptTool tool = new SetPromptTool(prompts);

    @Test
    void createsWithoutId() {
        when(prompts.create("Fix", "", "Go", 3L, 5L)).thenReturn(new PromptTemplate(1, "Fix", "", "Go", 3L, 5L));

        var text = tool.call(JsonMapper.shared().readTree("{\"name\":\"Fix\",\"text\":\"Go\",\"projectId\":3,\"defaultTeamId\":5}"));

        assertThat(text).contains("id 1");
    }

    @Test
    void updatesWithId() {
        when(prompts.update(1, "Fix", "d", "Go", null, null)).thenReturn(new PromptTemplate(1, "Fix", "d", "Go", null, null));

        tool.call(JsonMapper.shared().readTree("{\"id\":1,\"name\":\"Fix\",\"description\":\"d\",\"text\":\"Go\"}"));

        verify(prompts).update(1, "Fix", "d", "Go", null, null);
    }
}
