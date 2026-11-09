package com.github.krpkbhdn.ccmanager.preset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectPathRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {".git", ".GIT", "GIT~1", "git~1"})
    void rejectsGitFolderAndItsShortName(String segment) {
        assertThatThrownBy(() -> ProjectPathRules.requireSafeSegment(segment, segment + "/config")).hasMessageContaining(".git");
    }
}
