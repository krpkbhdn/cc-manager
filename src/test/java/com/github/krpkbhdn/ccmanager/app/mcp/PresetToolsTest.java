package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.BundledPresetLoader;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import com.github.krpkbhdn.ccmanager.preset.apply.ChangeAction;
import com.github.krpkbhdn.ccmanager.preset.apply.ComponentOutcome;
import com.github.krpkbhdn.ccmanager.preset.apply.OutcomeStatus;
import com.github.krpkbhdn.ccmanager.preset.apply.PlannedChange;
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyResult;
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

/** The preset tools over a real preset service on a temporary database. */
class PresetToolsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = "sk-very-secret";

    @TempDir
    Path temp;

    private ProjectPresetService presets;
    private final PluginService plugins = mock(PluginService.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final PresetApplyService apply = mock(PresetApplyService.class);

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        presets = new ProjectPresetService(JdbcClient.create(dataSource), new TransactionTemplate(new DataSourceTransactionManager(dataSource)), mock(BundledPresetLoader.class));
    }

    private static JsonNode args(String json) {
        return JSON.readTree(json);
    }

    private long createPreset(String name) {
        new CreatePresetTool(presets).call(args("{\"name\":\"" + name + "\",\"description\":\"d\"}"));
        return presets.list().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow().id();
    }

    private AddPresetComponentTool add() {
        return new AddPresetComponentTool(presets, plugins);
    }

    private long addComponent(long preset, String kind, String name, String fields) {
        add().call(args("{\"preset_id\":" + preset + ",\"kind\":\"" + kind + "\",\"name\":\"" + name + "\"," + fields + "}"));
        return presets.get(preset).orElseThrow().components().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }

    @Test
    void createsPresetsAndAddsEveryKind() {
        var id = createPreset("Web");

        addComponent(id, "SKILL", "review", "\"skill_markdown\":\"Check it\",\"description\":\"Reviews\"");
        addComponent(id, "SKILL", "files-skill", "\"files\":{\"SKILL.md\":\"x\"}");
        addComponent(id, "INSTRUCTION", "Rules", "\"markdown\":\"Be brief\"");
        addComponent(id, "AGENT", "helper", "\"content\":\"You help\"");
        addComponent(id, "FILE", "env-example", "\"path\":\".env.example\",\"content\":\"A=1\",\"gitignore\":[\".env\"]");
        addComponent(id, "MCP_SERVER", "db", "\"config\":{\"command\":\"npx\",\"env\":{\"TOKEN\":\"" + SECRET + "\"}}");
        addComponent(id, "SETTINGS", "settings", "\"fragment\":{\"env\":{\"K\":\"" + SECRET + "\"}}");
        addComponent(id, "MARKETPLACE", "mp", "\"source\":\"owner/repo\"");
        addComponent(id, "PLUGIN", "tool@mp", "\"source\":\"owner/repo\"");

        var kinds = presets.get(id).orElseThrow().components().stream().map(c -> c.kind()).collect(Collectors.toSet());
        assertThat(kinds).containsExactlyInAnyOrder(PresetComponentKind.values());
        var skill = presets.get(id).orElseThrow().components().get(0).payload().path("files").path("SKILL.md").asString();
        assertThat(skill).startsWith("---\nname: review\ndescription: Reviews\n---").endsWith("Check it");
    }

    @Test
    void passesValidationMessagesBack() {
        var id = createPreset("Web");

        assertThatThrownBy(() -> addComponent(id, "SKILL", "bad name", "\"files\":{\"SKILL.md\":\"x\"}")).hasMessageContaining("letters, digits");
        assertThatThrownBy(() -> addComponent(id, "FILE", "f", "\"path\":\"../x\",\"content\":\"\"")).hasMessageContaining("inside the project");
        assertThatThrownBy(() -> addComponent(id, "SKILL", "s", "\"skill_markdown\":\"x\"")).hasMessageContaining("description");
        assertThatThrownBy(() -> addComponent(id, "NOPE", "n", "\"markdown\":\"x\"")).hasMessageContaining("Unknown kind");
        assertThatThrownBy(() -> new CreatePresetTool(presets).call(args("{\"name\":\"Web\"}"))).hasMessageContaining("already exists");
    }

    @Test
    void pluginAddsItsKnownMarketplaceOnce() {
        when(plugins.marketplaces()).thenReturn(List.of(new Marketplace("mp", "github", "owner/repo", "/x")));
        var id = createPreset("Web");

        var message = add().call(args("{\"preset_id\":" + id + ",\"kind\":\"PLUGIN\",\"name\":\"a@mp\"}"));
        add().call(args("{\"preset_id\":" + id + ",\"kind\":\"PLUGIN\",\"name\":\"b@mp\"}"));

        assertThat(message).contains("Also added marketplace 'mp'");
        var components = presets.get(id).orElseThrow().components();
        assertThat(components).filteredOn(c -> c.kind() == PresetComponentKind.MARKETPLACE).singleElement()
                .satisfies(c -> assertThat(c.payload().path("source").asString()).isEqualTo("owner/repo"));
    }

    @Test
    void listAndGetRedactSecretsButGetWithComponentIdReturnsTextInFull() {
        var id = createPreset("Web");
        var mcp = addComponent(id, "MCP_SERVER", "db", "\"config\":{\"command\":\"npx\",\"args\":[\"--key=" + SECRET + "\"],\"env\":{\"TOKEN\":\"" + SECRET + "\"}}");
        addComponent(id, "SETTINGS", "settings", "\"fragment\":{\"env\":{\"K\":\"" + SECRET + "\"}}");
        var rule = addComponent(id, "INSTRUCTION", "Rules", "\"markdown\":\"" + "word ".repeat(100) + "END\"");

        var list = new ListPresetsTool(presets).call(args("{}"));
        var overview = new GetPresetTool(presets).call(args("{\"preset_id\":" + id + "}"));
        var one = new GetPresetTool(presets).call(args("{\"preset_id\":" + id + ",\"component_id\":" + rule + "}"));
        var redacted = new GetPresetTool(presets).call(args("{\"preset_id\":" + id + ",\"component_id\":" + mcp + "}"));

        assertThat(list).contains("\"name\":\"Web\"", "\"INSTRUCTION\":1", "\"MCP_SERVER\":1");
        assertThat(overview).doesNotContain(SECRET).contains("npx").contains("values hidden").doesNotContain("END");
        assertThat(one).contains("END");
        assertThat(redacted).doesNotContain(SECRET);
    }

    @Test
    void updatesMovesAndRemovesComponentsAndPresets() {
        var id = createPreset("Web");
        var first = addComponent(id, "INSTRUCTION", "One", "\"markdown\":\"a\"");
        var second = addComponent(id, "INSTRUCTION", "Two", "\"markdown\":\"b\"");

        new UpdatePresetComponentTool(presets).call(args("{\"component_id\":" + first + ",\"markdown\":\"changed\"}"));
        new MovePresetComponentTool(presets).call(args("{\"component_id\":" + second + ",\"direction\":\"up\"}"));
        assertThat(presets.get(id).orElseThrow().components()).extracting(c -> c.name()).containsExactly("Two", "One");
        assertThat(presets.get(id).orElseThrow().components().get(1).payload().path("markdown").asString()).isEqualTo("changed");
        assertThatThrownBy(() -> new MovePresetComponentTool(presets).call(args("{\"component_id\":" + second + ",\"direction\":\"left\"}"))).hasMessageContaining("up or down");

        new RemovePresetComponentTool(presets).call(args("{\"component_id\":" + first + "}"));
        new UpdatePresetTool(presets).call(args("{\"preset_id\":" + id + ",\"name\":\"Renamed\"}"));
        assertThat(presets.get(id).orElseThrow().name()).isEqualTo("Renamed");
        assertThat(presets.get(id).orElseThrow().description()).isEqualTo("d");
        assertThat(presets.get(id).orElseThrow().components()).hasSize(1);

        new DuplicatePresetTool(presets).call(args("{\"preset_id\":" + id + ",\"name\":\"Copy\"}"));
        assertThat(presets.list()).hasSize(2);
        new DeletePresetTool(presets).call(args("{\"preset_id\":" + id + "}"));
        assertThat(presets.list()).extracting(p -> p.name()).containsExactly("Copy");
        assertThatThrownBy(() -> new DeletePresetTool(presets).call(args("{\"preset_id\":" + id + "}"))).hasMessageContaining("not found");
    }

    @Test
    void restoreRejectsUnknownBundledKey() {
        assertThatThrownBy(() -> new RestorePresetTool(presets).call(args("{\"key\":\"nope\"}"))).hasMessageContaining("No bundled preset");
    }

    @Test
    void planIsReadOnlyAndApplyNeedsProjectAndReturnsOutcomes() {
        var id = createPreset("Web");
        var component = addComponent(id, "INSTRUCTION", "Rules", "\"markdown\":\"a\"");
        var project = new Project(7, "demo", temp, Instant.EPOCH);
        when(projects.find(7)).thenReturn(Optional.of(project));
        when(projects.find(8)).thenReturn(Optional.empty());
        when(apply.plan(id, temp)).thenReturn(List.of(new PlannedChange(component, PresetComponentKind.INSTRUCTION, "Rules", ChangeAction.CREATE, "New section", null)));
        when(apply.apply(eq(7L), eq(id), eq(temp), any())).thenReturn(new PresetApplyResult(
                List.of(new ComponentOutcome(component, PresetComponentKind.INSTRUCTION, "Rules", OutcomeStatus.APPLIED, "Written")), true));
        var plan = new PlanPresetTool(presets, apply, projects);
        var applyTool = new ApplyPresetTool(presets, apply, projects);

        assertThat(plan.autoApproved()).isTrue();
        assertThat(applyTool.autoApproved()).isFalse();
        assertThat(plan.call(args("{\"project_id\":7,\"preset_id\":" + id + "}"))).contains("CREATE INSTRUCTION 'Rules'", "New section");
        var result = applyTool.call(args("{\"project_id\":7,\"preset_id\":" + id + "}"));
        assertThat(result).contains("'demo'", "APPLIED INSTRUCTION 'Rules': Written", "recorded");
        verify(apply).apply(7L, id, temp, Set.of(component));
        assertThatThrownBy(() -> applyTool.call(args("{\"project_id\":8,\"preset_id\":" + id + "}"))).hasMessageContaining("Project not found");
        assertThatThrownBy(() -> applyTool.call(args("{\"project_id\":7,\"preset_id\":" + id + ",\"component_ids\":[999]}"))).hasMessageContaining("belong to the preset");
    }

    @Test
    void onlyTheReadTrioIsAutoApprovedAndNamesAreUnique() {
        var tools = AssistantTools.all();

        assertThat(tools).filteredOn(t -> t.name().contains("preset")).filteredOn(AssistantMcpTool::autoApproved)
                .extracting(AssistantMcpTool::name).containsExactlyInAnyOrder("list_presets", "get_preset", "plan_preset");
        assertThat(tools).filteredOn(t -> t.name().contains("preset")).hasSize(13);
        assertThat(tools.stream().map(AssistantMcpTool::name).distinct().count()).isEqualTo(tools.size());
    }
}
