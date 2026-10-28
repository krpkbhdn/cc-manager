package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GetHelpToolTest {

    private static final List<String> TOPICS = List.of("Screens", "Sidebar", "Sessions", "Groups", "Teams", "Prompts", "Queue", "Runs", "Git",
            "Extensions (skills, plugins, MCP)", "Shortcuts", "Command palette", "Assistant");

    private final GetHelpTool tool = new GetHelpTool();

    private static JsonNode args(String json) {
        return JsonMapper.shared().readTree(json);
    }

    @Test
    void withoutTopicListsEveryTopic() {
        assertThat(tool.call(args("{}"))).contains(TOPICS);
    }

    @Test
    void returnsOneSectionCaseInsensitively() {
        var result = tool.call(args("{\"topic\":\"queue\"}"));

        assertThat(result).contains("prompt queue").doesNotContain("Run configurations");
    }

    @Test
    void unknownTopicListsTopics() {
        assertThatThrownBy(() -> tool.call(args("{\"topic\":\"nope\"}"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope").hasMessageContaining("Sessions");
    }

    @Test
    void isAutoApproved() {
        assertThat(tool.autoApproved()).isTrue();
    }
}
