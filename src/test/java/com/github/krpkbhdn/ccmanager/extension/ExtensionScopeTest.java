package com.github.krpkbhdn.ccmanager.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExtensionScopeTest {

    @Test
    void cliValueIsLowerCaseAndParsesBack() {
        for (var scope : ExtensionScope.values()) {
            assertThat(ExtensionScope.fromCli(scope.cliValue())).contains(scope);
        }
        assertThat(ExtensionScope.PROJECT.cliValue()).isEqualTo("project");
    }

    @Test
    void unknownCliValueIsEmpty() {
        assertThat(ExtensionScope.fromCli("managed")).isEmpty();
        assertThat(ExtensionScope.fromCli("")).isEmpty();
    }

    @Test
    void userScopeRunsAnywhereOthersNeedTheProject() {
        var project = Path.of("/work/p");

        assertThat(ExtensionScope.USER.workingDirectory(project)).isNull();
        assertThat(ExtensionScope.LOCAL.workingDirectory(project)).isEqualTo(project);
        assertThatThrownBy(() -> ExtensionScope.PROJECT.workingDirectory(null)).isInstanceOf(ExtensionException.class);
    }
}
