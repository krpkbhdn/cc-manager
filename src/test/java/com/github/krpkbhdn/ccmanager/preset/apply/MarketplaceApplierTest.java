package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MarketplaceApplierTest {

    private static final String SOURCE = "https://user:s3cret-token@git.example.com/org/repo.git";

    private final PluginService plugins = mock(PluginService.class);
    private final MarketplaceApplier applier = new MarketplaceApplier(plugins);

    private static PresetComponent component() {
        return new PresetComponent(1, PresetComponentKind.MARKETPLACE, "m", JsonMapper.builder().build().createObjectNode().put("source", SOURCE));
    }

    @Test
    void planDetailHidesCredentialsAndPathOfTheSource() {
        when(plugins.marketplaces()).thenReturn(List.of());

        var change = applier.plan(component(), Path.of("."));

        assertThat(change.detail()).contains("git.example.com").doesNotContain("s3cret-token").doesNotContain("user:").doesNotContain("org/repo");
    }

    @Test
    void planHasNoRawSourcePreview() {
        when(plugins.marketplaces()).thenReturn(List.of());

        assertThat(applier.plan(component(), Path.of(".")).preview()).isNull();
    }
}
