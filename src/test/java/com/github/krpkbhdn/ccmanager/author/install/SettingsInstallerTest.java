package com.github.krpkbhdn.ccmanager.author.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.author.AuthorProfileLoader;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsInstallerTest {

    @TempDir
    Path configDirectory;

    SettingsInstaller installer;
    final com.github.krpkbhdn.ccmanager.author.AuthorItem item =
            new AuthorProfileLoader().catalog().byId("user.settings.attribution").orElseThrow();

    @BeforeEach
    void setUp() {
        var host = mock(HostEnvironment.class);
        when(host.claudeConfigDirectory()).thenReturn(configDirectory);
        installer = new SettingsInstaller(host);
    }

    @Test
    void createsTheSettingsFile() throws Exception {
        var outcome = installer.install(item);

        assertThat(outcome.installed()).hasSize(1);
        assertThat(Files.readString(configDirectory.resolve("settings.json"))).contains("attribution");
    }

    @Test
    void secondInstallIsAlreadyPresent() {
        installer.install(item);

        assertThat(installer.install(item).alreadyPresent()).hasSize(1);
    }

    @Test
    void differingValueNeedsAttentionNamingTheKeyAndIsNotRecordedAsFailure() throws Exception {
        var original = "{\"attribution\":{\"commit\":\"mine\"}}";
        Files.writeString(configDirectory.resolve("settings.json"), original);

        var outcome = installer.install(item);

        assertThat(outcome.needsAttention()).singleElement().asString().contains("attribution.commit");
        assertThat(outcome.hasFailures()).isFalse();
        assertThat(Files.readString(configDirectory.resolve("settings.json"))).contains("\"mine\"");
    }

    @Test
    void invalidJsonIsAFailureAndTheFileSurvives() throws Exception {
        Files.writeString(configDirectory.resolve("settings.json"), "{ broken");

        var outcome = installer.install(item);

        assertThat(outcome.hasFailures()).isTrue();
        assertThat(Files.readString(configDirectory.resolve("settings.json"))).isEqualTo("{ broken");
    }
}
