package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskBranchNamesTest {

    @Test
    void slugLowercasesAndJoinsRunsWithDash() {
        assertThat(TaskBranchNames.slugOf("  Fix the  Login, bug!! ")).isEqualTo("fix-the-login-bug");
    }

    @Test
    void slugFallsBackToTaskWhenNothingUsable() {
        assertThat(TaskBranchNames.slugOf("")).isEqualTo("task");
        assertThat(TaskBranchNames.slugOf(null)).isEqualTo("task");
        assertThat(TaskBranchNames.slugOf("исправить баг ???")).isEqualTo("task");
    }

    @Test
    void slugKeepsAsciiPartOfMixedText() {
        assertThat(TaskBranchNames.slugOf("баг в API v2")).isEqualTo("api-v2");
    }

    @Test
    void slugIsCutToFortyCharactersWithoutTrailingDash() {
        var slug = TaskBranchNames.slugOf("a".repeat(39) + " bbbbbb");
        assertThat(slug).isEqualTo("a".repeat(39));
        assertThat(TaskBranchNames.slugOf("x".repeat(100))).hasSize(40);
    }

    @Test
    void branchAddsNumericSuffixUntilFree() {
        var existing = List.of("task/add-login", "task/add-login-2");
        assertThat(TaskBranchNames.branchFor("Add login", existing)).isEqualTo("task/add-login-3");
        assertThat(TaskBranchNames.branchFor("Add login", List.of())).isEqualTo("task/add-login");
    }

    @Test
    void branchGivesUpAfterBoundedAttempts() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TaskBranchNames.branchFor("x", name -> true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No free branch name");
    }
}
