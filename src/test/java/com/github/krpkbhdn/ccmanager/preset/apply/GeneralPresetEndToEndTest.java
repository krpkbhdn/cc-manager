package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.preset.BundledPresetLoader;
import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

/** Real bundled General preset, real SQLite, real project folder; only the plugin and MCP CLIs are mocked. */
class GeneralPresetEndToEndTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path temp;

    Path project;
    JdbcClient jdbc;
    ProjectPresetService presets;
    PresetApplyService service;
    ProjectPreset general;
    Set<Long> all;

    @BeforeEach
    void setUp() throws IOException {
        project = Files.createDirectory(temp.resolve("proj"));
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("e2e.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO project (id, name, path) VALUES (5, 'p', ?)").param(project.toString()).update();
        presets = new ProjectPresetService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new BundledPresetLoader());
        presets.syncBundled();
        var plugins = mock(PluginService.class);
        when(plugins.install(anyString(), any(), any())).thenReturn(new PluginInstallResult(true, "", null));
        service = new PresetApplyService(presets, new PresetApplicationService(jdbc), List.of(new MarketplaceApplier(plugins), new PluginApplier(plugins),
                new McpServerApplier(mock(McpService.class)), new SkillApplier(), new AgentApplier(), new FileApplier(), new InstructionApplier(), new SettingsApplier()));
        general = presets.list().getFirst();
        all = general.components().stream().map(PresetComponent::id).collect(Collectors.toSet());
    }

    @Test
    void generalIsFirstAndAppliesToAnEmptyFolder() throws IOException {
        assertThat(general.name()).isEqualTo("General");

        var result = service.apply(5, general.id(), project, all);

        assertThat(result.outcomes()).extracting(ComponentOutcome::status).containsOnly(OutcomeStatus.APPLIED);
        var claude = Files.readString(project.resolve("CLAUDE.md"));
        for (var name : List.of("Duplicate code", "Clean code", "Comments", "Conventions")) {
            assertThat(claude).contains("## " + name);
        }
        assertThat(claude.split("cc-manager:begin", -1)).hasSize(5);
        assertThat(claude.split("cc-manager:end", -1)).hasSize(5);
        assertThat(project.resolve(".claude/skills/jscpd/SKILL.md")).isNotEmptyFile();
        assertThat(project.resolve(".claude/skills/dry-refactoring/SKILL.md")).isNotEmptyFile();
        assertThat(project.resolve(".jscpd.json")).isNotEmptyFile();
        assertThat(Files.readAllLines(project.resolve(".gitignore"))).containsOnlyOnce("jscpd-report/");
        assertThat(new PresetApplicationService(jdbc).applications(5)).containsEntry(general.id(), general.revision());
    }

    @Test
    void reapplyIsAllPresentAndByteIdentical() throws IOException {
        service.apply(5, general.id(), project, all);
        var before = Files.readString(project.resolve("CLAUDE.md")) + Files.readString(project.resolve(".gitignore"));

        var plan = service.plan(general.id(), project);
        var again = service.apply(5, general.id(), project, all);

        assertThat(plan).extracting(PlannedChange::action).containsOnly(ChangeAction.PRESENT);
        assertThat(again.recorded()).isTrue();
        assertThat(Files.readString(project.resolve("CLAUDE.md")) + Files.readString(project.resolve(".gitignore"))).isEqualTo(before);
    }

    @Test
    void editedWrittenSkillIsAConflictAndStaysUntouched() throws IOException {
        service.apply(5, general.id(), project, all);
        var skill = project.resolve(".claude/skills/jscpd/SKILL.md");
        Files.writeString(skill, "my own version");

        var plan = service.plan(general.id(), project);
        var result = service.apply(5, general.id(), project, all);

        assertThat(plan).filteredOn(c -> c.title().equals("jscpd")).extracting(PlannedChange::action).containsExactly(ChangeAction.CONFLICT);
        assertThat(skill).hasContent("my own version");
        assertThat(result.outcomes()).filteredOn(o -> o.status() == OutcomeStatus.APPLIED).isEmpty();
    }

    @Test
    void crlfClaudeMdKeepsHandWrittenContentAndLineEndings() throws IOException {
        Files.writeString(project.resolve("CLAUDE.md"), "# Mine\r\n\r\nhand written line\r\n");

        service.apply(5, general.id(), project, all);

        var text = Files.readString(project.resolve("CLAUDE.md"));
        assertThat(text).startsWith("# Mine\r\n\r\nhand written line\r\n").contains("## Clean code").doesNotContainPattern("[^\\r]\\n");
        assertThat(service.plan(general.id(), project)).filteredOn(c -> c.kind() == PresetComponentKind.INSTRUCTION)
                .extracting(PlannedChange::action).containsOnly(ChangeAction.PRESENT);
    }

    @Test
    void parentAndAbsolutePathFileComponentsAreRejectedByTheService() {
        var id = presets.create("Evil", "").id();
        for (var path : List.of("../x", "/abs-evil.txt", "C:\\abs-evil.txt")) {
            assertThatThrownBy(() -> presets.addComponent(id, PresetComponentKind.FILE, "f", JSON.createObjectNode().put("path", path).put("content", "x")))
                    .as(path).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void escapingFilePathPlantedInTheDatabaseIsNeverWritten() {
        var id = presets.create("Evil", "").id();
        var planted = presets.addComponent(id, PresetComponentKind.FILE, "f", JSON.createObjectNode().put("path", "a.txt").put("content", "x"));
        jdbc.sql("UPDATE project_preset_component SET payload = ? WHERE id = ?").param("{\"path\":\"../x\",\"content\":\"pwn\"}").param(planted.id()).update();

        var plan = service.plan(id, project);
        service.apply(5, id, project, Set.of(planted.id()));

        assertThat(plan).extracting(PlannedChange::action).containsOnly(ChangeAction.CONFLICT);
        assertThat(temp.resolve("x")).doesNotExist();
    }
}
