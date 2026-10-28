package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.github.krpkbhdn.ccmanager.app.AppControl;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NavigateToolTest {

    private final AppControl ui = mock(AppControl.class);
    private final AppControlRegistry registry = new AppControlRegistry();
    private final NavigateTool tool = new NavigateTool(registry);

    @Test
    void navigatesToAKnownRoute() {
        registry.connect(ui);

        tool.call(JsonMapper.shared().readTree("{\"route\":\"teams\"}"));

        verify(ui).navigate("teams");
    }

    @Test
    void rejectsUnknownRoute() {
        registry.connect(ui);

        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"route\":\"nope\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown route");
    }
}
