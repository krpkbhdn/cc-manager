package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class AgentApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;

    private final AgentApplier applier = new AgentApplier();

    private static PresetComponent agent(String name, String content) {
        return new PresetComponent(1, PresetComponentKind.AGENT, name, JSON.createObjectNode().put("content", content));
    }

    @Test
    void missingAgentIsCreatedUnderClaudeAgents() throws Exception {
        var component = agent("reviewer", "---\nname: reviewer\n---\n");

        assertThat(applier.plan(component, dir).action()).isEqualTo(ChangeAction.CREATE);
        applier.apply(component, dir);

        assertThat(Files.readString(dir.resolve(".claude/agents/reviewer.md"))).isEqualTo("---\nname: reviewer\n---\n");
    }

    @Test
    void differingExistingAgentIsAConflictAndNotOverwritten() throws Exception {
        Files.createDirectories(dir.resolve(".claude/agents"));
        Files.writeString(dir.resolve(".claude/agents/reviewer.md"), "mine");

        assertThat(applier.plan(agent("reviewer", "other"), dir).action()).isEqualTo(ChangeAction.CONFLICT);
        assertThatThrownBy(() -> applier.apply(agent("reviewer", "other"), dir)).isInstanceOf(IOException.class);
        assertThat(Files.readString(dir.resolve(".claude/agents/reviewer.md"))).isEqualTo("mine");
    }

    @Test
    void newAgentDeclaringHooksNeedsConfirmation() throws Exception {
        var plan = applier.plan(agent("a", "---\nname: a\nhooks:\n  PreToolUse: []\n---\nbody"), dir);

        assertThat(plan.action()).isEqualTo(ChangeAction.NEEDS_CONFIRMATION);
        assertThat(applier.plan(agent("b", "---\nname: b\n---\nhooks: in the body"), dir).action()).isEqualTo(ChangeAction.CREATE);
    }

    @Test
    void identicalAgentIsPresent()throws Exception {
        applier.apply(agent("a", "x"), dir);

        assertThat(applier.plan(agent("a", "x"), dir).action()).isEqualTo(ChangeAction.PRESENT);
    }

    @Test
    void agentNameThatEscapesTheAgentsFolderIsRejected() {
        assertThatThrownBy(() -> applier.apply(agent("../../evil", "x"), dir)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applier.apply(agent("C:evil", "x"), dir)).isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("evil.md")).doesNotExist();
        assertThat(dir.getParent().resolve("evil.md")).doesNotExist();
    }
}
