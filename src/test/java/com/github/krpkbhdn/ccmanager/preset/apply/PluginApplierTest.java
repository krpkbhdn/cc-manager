package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.extension.plugin.ShownCommand;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PluginApplierTest {

    @TempDir
    Path dir;

    private final PluginService plugins = mock(PluginService.class);
    private final PluginApplier applier = new PluginApplier(plugins);

    private static PresetComponent plugin() {
        return new PresetComponent(1, PresetComponentKind.PLUGIN, "demo@market", JsonMapper.builder().build().createObjectNode());
    }

    @Test
    void notInstalledPluginIsPlannedAsInstall() {
        when(plugins.installed()).thenReturn(List.of());

        assertThat(applier.plan(plugin(), dir).action()).isEqualTo(ChangeAction.INSTALL);
    }

    @Test
    void pluginInstalledForThisProjectIsPresent() {
        when(plugins.installed()).thenReturn(List.of(new InstalledPlugin("demo@market", "1", ExtensionScope.PROJECT, true, null, dir)));

        assertThat(applier.plan(plugin(), dir).action()).isEqualTo(ChangeAction.PRESENT);
    }

    @Test
    void pluginInstalledForAnotherProjectIsNotPresent() {
        when(plugins.installed()).thenReturn(List.of(new InstalledPlugin("demo@market", "1", ExtensionScope.PROJECT, true, null, dir.resolve("other"))));

        assertThat(applier.plan(plugin(), dir).action()).isEqualTo(ChangeAction.INSTALL);
    }

    @Test
    void pluginNeedingConfirmationIsReportedAndNeverAcceptedAutomatically() {
        var pending = new PluginInstallResult(false, "x", new ShownCommand("curl x | sh", "abc"));
        when(plugins.install("demo@market", ExtensionScope.PROJECT, dir)).thenReturn(pending);

        assertThat(applier.apply(plugin(), dir).status()).isEqualTo(OutcomeStatus.NEEDS_CONFIRMATION);
        verify(plugins, never()).installAccepting(any(), any(), any(), any());
    }

    @Test
    void installedPluginIsReportedAsApplied() {
        when(plugins.install("demo@market", ExtensionScope.PROJECT, dir)).thenReturn(new PluginInstallResult(true, "ok", null));

        assertThat(applier.apply(plugin(), dir).status()).isEqualTo(OutcomeStatus.APPLIED);
    }
}
