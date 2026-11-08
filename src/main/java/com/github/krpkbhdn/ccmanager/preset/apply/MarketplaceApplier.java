package com.github.krpkbhdn.ccmanager.preset.apply;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpTargetRedaction;
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
class MarketplaceApplier implements PresetComponentApplier {

    private final PluginService plugins;

    MarketplaceApplier(PluginService plugins) {
        this.plugins = plugins;
    }

    @Override
    public PresetComponentKind kind() {
        return PresetComponentKind.MARKETPLACE;
    }

    @Override
    public PlannedChange plan(PresetComponent component, Path projectDir) {
        var known = plugins.marketplaces().stream().map(Marketplace::name).anyMatch(component.name()::equals);
        return known ? PlannedChange.of(component, ChangeAction.PRESENT, "Marketplace already added")
                : PlannedChange.of(component, ChangeAction.INSTALL, "Add the marketplace " + McpTargetRedaction.redact(source(component)))
                        .withPreview(source(component));
    }

    @Override
    public ComponentOutcome apply(PresetComponent component, Path projectDir) {
        plugins.addMarketplace(source(component), ExtensionScope.PROJECT, projectDir);
        return ComponentOutcome.of(component, OutcomeStatus.APPLIED, "Marketplace added");
    }

    private static String source(PresetComponent component) {
        return component.payload().path("source").asString("");
    }
}
