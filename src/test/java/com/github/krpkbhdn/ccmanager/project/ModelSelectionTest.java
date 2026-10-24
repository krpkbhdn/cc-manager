package com.github.krpkbhdn.ccmanager.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelSelectionTest {

    @Test
    void defaultPassesNothing() {
        assertThat(ModelSelection.DEFAULT.arguments()).isEmpty();
        assertThat(new ModelSelection(" ", "")).isEqualTo(ModelSelection.DEFAULT);
    }

    @Test
    void modelAndEffortBecomeFlags() {
        assertThat(new ModelSelection(" opus[1m] ", "high").arguments()).containsExactly("--model", "opus[1m]", "--effort", "high");
        assertThat(new ModelSelection(null, "max").arguments()).containsExactly("--effort", "max");
    }

    @Test
    void acceptsFullModelNames() {
        assertThat(new ModelSelection("claude-opus-4-7", null).model()).isEqualTo("claude-opus-4-7");
    }

    @Test
    void rejectsFlagLikeOrSpacedModelAndUnknownEffort() {
        assertThatThrownBy(() -> new ModelSelection("--dangerously-skip-permissions", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelSelection("opus 4", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelSelection(null, "ultra")).hasMessageContaining("ultra");
    }
}
