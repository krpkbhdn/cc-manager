package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.author.install.ExtensionInstaller;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
class PluginApplier implements PresetComponentApplier {

    private final PluginService plugins;

    PluginApplier(PluginService plugins) {
        this.plugins = plugins;
    }

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.PLUGIN;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) {
        var present = plugins.installed().stream()
                .anyMatch(plugin -> ExtensionInstaller.sameInstallation(plugin, component.name(), ExtensionScope.PROJECT, projectDir));
        return present ? PlannedChange.of(component, ChangeAction.PRESENT, "Plugin already installed for this project")
                : PlannedChange.of(component, ChangeAction.INSTALL, "Install the plugin for this project");
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) {
        var result = plugins.install(component.name(), ExtensionScope.PROJECT, projectDir);
        return result.needsConfirmation()
                ? ComponentOutcome.of(component, OutcomeStatus.NEEDS_CONFIRMATION, "The plugin runs a command that needs your confirmation, install it on the Plugins page")
                : ComponentOutcome.of(component, OutcomeStatus.APPLIED, "Plugin installed");
    }
}
