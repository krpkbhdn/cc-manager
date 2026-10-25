package com.github.krpkbhdn.ccmanager.extension.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionProperties;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.RecordingCli;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginServiceTest {

    private static final Path PROJECT = Path.of("/work/project");
    private final RecordingCli cli = new RecordingCli();
    private final PluginService service = new PluginService(cli, new ExtensionProperties(30, 180, 90, 300));

    @Test
    void detailsRunInTheProjectDirectoryOfTheInstallation() {
        cli.answering("a 1.0.0");

        service.details("a@m", PROJECT);

        assertThat(cli.lastCall()).containsExactly("plugin", "details", "a@m");
        assertThat(cli.directories).containsExactly(PROJECT);
    }

    @Test
    void projectScopeInstallRunsInTheProjectDirectory() {
        cli.answering("{\"message\":\"ok\"}");

        var result = service.install("a@m", ExtensionScope.PROJECT, PROJECT);

        assertThat(result.installed()).isTrue();
        assertThat(cli.lastCall()).containsExactly("plugin", "install", "a@m", "--scope", "project", "--json");
        assertThat(cli.directories).containsExactly(PROJECT);
    }

    @Test
    void installNeverAcceptsAMarketplaceCommandOnItsOwn() {
        cli.answering(1, "{\"shownCommand\":{\"command\":\"npx x\",\"sha256\":\"abc\"}}", "");

        var result = service.install("a@m", ExtensionScope.USER, null);

        assertThat(result.pendingCommand().sha256()).isEqualTo("abc");
        assertThat(cli.lastCall()).doesNotContain("-y", "--yes", "--accept-command");
    }

    @Test
    void installAcceptingPassesExactlyTheConfirmedHash() {
        cli.answering("{\"message\":\"ok\"}");

        service.installAccepting("a@m", ExtensionScope.USER, null, new ShownCommand("npx x", "abc"));

        assertThat(cli.lastCall()).containsSubsequence("--accept-command", "abc").doesNotContain("-y", "--yes");
        assertThat(cli.directories).containsExactly((Path) null);
    }

    @Test
    void failedInstallSurfacesTheCliMessage() {
        cli.answering(1, "", "Plugin \"zzz\" not found");

        assertThatThrownBy(() -> service.install("zzz", ExtensionScope.USER, null))
                .isInstanceOf(ExtensionException.class)
                .hasMessageContaining("Plugin \"zzz\" not found");
    }

    @Test
    void projectScopeWithoutProjectIsRejectedBeforeRunningClaude() {
        assertThatThrownBy(() -> service.setEnabled("a@m", ExtensionScope.LOCAL, null, true))
                .isInstanceOf(ExtensionException.class);
        assertThat(cli.calls).isEmpty();
    }

    @Test
    void enableAndDisableUseTheirOwnCommands() {
        service.setEnabled("a@m", ExtensionScope.USER, null, false);

        assertThat(cli.lastCall()).containsExactly("plugin", "disable", "a@m", "--scope", "user", "--json");
    }

    @Test
    void availableCatalogueIsFetchedOnceUntilRefreshed() {
        cli.answering("{\"available\":[{\"pluginId\":\"x@m\",\"name\":\"x\"}]}");

        service.available();
        service.available();
        assertThat(cli.calls).hasSize(1);

        service.refreshAvailable();
        assertThat(cli.calls).hasSize(2);
    }

    @Test
    void updatingAllMarketplacesPassesNoName() {
        service.updateMarketplace(null);

        assertThat(cli.lastCall()).containsExactly("plugin", "marketplace", "update");
    }
}
