package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.BundledPresetLoader;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PresetToolsFollowUpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ENV_VALUE = "real-password-123";

    @TempDir
    Path temp;

    private ProjectPresetService presets;
    private final PluginService plugins = mock(PluginService.class);
    private long id;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        presets = new ProjectPresetService(JdbcClient.create(dataSource), new TransactionTemplate(new DataSourceTransactionManager(dataSource)), mock(BundledPresetLoader.class));
        new CreatePresetTool(presets).call(args("{\"name\":\"Web\",\"description\":\"d\"}"));
        id = presets.list().get(0).id();
    }

    private static JsonNode args(String json) {
        return JSON.readTree(json);
    }

    private String addPlugin(String extra) {
        return new AddPresetComponentTool(presets, plugins).call(args("{\"preset_id\":" + id + ",\"kind\":\"PLUGIN\",\"name\":\"a@mp\"" + extra + "}"));
    }

    @Test
    void getPresetWithComponentIdNeverReturnsFileContent() {
        new AddPresetComponentTool(presets, plugins).call(args("{\"preset_id\":" + id + ",\"kind\":\"FILE\",\"name\":\"env\",\"path\":\".env.example\",\"content\":\"PASSWORD=" + ENV_VALUE + "\"}"));
        var file = presets.get(id).orElseThrow().components().get(0).id();

        var result = new GetPresetTool(presets).call(args("{\"preset_id\":" + id + ",\"component_id\":" + file + "}"));

        assertThat(result).doesNotContain(ENV_VALUE).contains(".env.example");
    }

    @Test
    void blankSourceWithUnknownMarketplaceSavesNothing() {
        assertThatThrownBy(() -> addPlugin(",\"source\":\"\"")).hasMessageContaining("pass its source");

        assertThat(presets.get(id).orElseThrow().components()).isEmpty();
    }

    @Test
    void retryWithSourceAfterFailureWorks() {
        assertThatThrownBy(() -> addPlugin(",\"source\":\"  \"")).isInstanceOf(IllegalArgumentException.class);

        assertThat(addPlugin(",\"source\":\"owner/repo\"")).contains("Also added marketplace 'mp'");
        assertThat(presets.get(id).orElseThrow().components()).hasSize(2);
    }

    @Test
    void knownMarketplaceWorksWithBlankSource() {
        when(plugins.marketplaces()).thenReturn(List.of(new Marketplace("mp", "github", "owner/repo", "/x")));

        assertThat(addPlugin(",\"source\":\"\"")).contains("Also added marketplace 'mp'");
    }
}
