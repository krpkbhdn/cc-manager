package com.github.krpkbhdn.ccmanager.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

class ProjectPresetServiceTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path temp;

    JdbcClient jdbc;
    DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    private ProjectPresetService service(BundledPreset... presets) {
        var service = new ProjectPresetService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new BundledPresetLoader(List.of(presets)));
        service.syncBundled();
        return service;
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    private static PresetComponentSpec instruction(String name, String markdown) {
        return new PresetComponentSpec(PresetComponentKind.INSTRUCTION, name, JSON.createObjectNode().put("markdown", markdown));
    }

    private static BundledPreset web(int version, PresetComponentSpec... components) {
        return new BundledPreset("web", version, "Web", "Web app", List.of(components));
    }

    private long project(String name) {
        return jdbc.sql("INSERT INTO project (name, path) VALUES (?, ?) RETURNING id").param(name).param("/p/" + name).query(Long.class).single();
    }

    @Test
    void createUpdateDuplicateAndDeleteUserPreset() {
        var service = service();
        var created = service.create("  Mine ", "desc");
        assertThat(created.name()).isEqualTo("Mine");
        assertThat(created.revision()).isEqualTo(1);

        var updated = service.update(created.id(), "Mine 2", "d2");
        assertThat(updated.name()).isEqualTo("Mine 2");
        assertThat(updated.edited()).isFalse();

        service.addComponent(created.id(), PresetComponentKind.INSTRUCTION, "Rules", json("{\"markdown\":\"x\"}"));
        var copy = service.duplicate(created.id(), "Copy");
        assertThat(copy.components()).extracting(PresetComponent::name).containsExactly("Rules");

        service.delete(created.id());
        assertThat(service.list()).extracting(ProjectPreset::name).containsExactly("Copy");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM project_preset WHERE id = ?").param(created.id()).query(Long.class).single()).isZero();
    }

    @Test
    void rejectsBlankAndDuplicateNames() {
        var service = service();
        service.create("A", "");

        assertThatThrownBy(() -> service.create(" ", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("A", "")).hasMessageContaining("already exists");
    }

    @Test
    void componentsAreAddedUpdatedMovedAndRemovedWithRevisionIncrements() {
        var service = service();
        var id = service.create("P", "").id();
        var first = service.addComponent(id, PresetComponentKind.INSTRUCTION, "One", json("{\"markdown\":\"1\"}"));
        var second = service.addComponent(id, PresetComponentKind.INSTRUCTION, "reviewer", json("{\"markdown\":\"c\"}"));
        assertThat(service.get(id).orElseThrow().revision()).isEqualTo(3);

        service.moveComponent(second.id(), true);
        assertThat(names(service, id)).containsExactly("reviewer", "One");
        service.moveComponent(second.id(), true);
        assertThat(names(service, id)).containsExactly("reviewer", "One");

        service.updateComponent(first.id(), "Uno", json("{\"markdown\":\"2\"}"));
        assertThat(names(service, id)).containsExactly("reviewer", "Uno");

        service.removeComponent(second.id());
        var preset = service.get(id).orElseThrow();
        assertThat(names(service, id)).containsExactly("Uno");
        assertThat(preset.revision()).isEqualTo(1 + 2 + 1 + 1 + 1);
    }

    private static List<String> names(ProjectPresetService service, long id) {
        return service.get(id).orElseThrow().components().stream().map(PresetComponent::name).toList();
    }

    @Test
    void rejectsInvalidComponents() {
        var service = service();
        var id = service.create("P", "").id();
        service.addComponent(id, PresetComponentKind.INSTRUCTION, "One", json("{\"markdown\":\"1\"}"));
        service.addComponent(id, PresetComponentKind.SETTINGS, "settings", json("{\"fragment\":{\"a\":1}}"));

        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.INSTRUCTION, "One", json("{\"markdown\":\"2\"}"))).hasMessageContaining("already has");
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SETTINGS, "other", json("{\"fragment\":{}}"))).hasMessageContaining("only one settings");
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SETTINGS, "s2", json("{\"fragment\":[1]}"))).hasMessageContaining("JSON object");
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.INSTRUCTION, " ", json("{\"markdown\":\"x\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.INSTRUCTION, "Empty", json("{\"markdown\":\" \"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.MCP_SERVER, "m", json("{\"config\":\"\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.PLUGIN, "no-marketplace", json("{}"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.get(id).orElseThrow().components()).hasSize(2);
    }

    @Test
    void rejectsPathsOutsideTheProject() {
        var service = service();
        var id = service.create("P", "").id();

        for (var path : List.of("../x", "a/../../x", "/etc/passwd", "C:/x", "C:\\x", "..\\x", "", ".")) {
            var payload = JSON.createObjectNode().put("path", path).put("content", "c");
            assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.FILE, "f", payload)).as(path).isInstanceOf(IllegalArgumentException.class);
        }
        var skill = JSON.createObjectNode();
        skill.putObject("files").put("../SKILL.md", "x");
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SKILL, "s", skill)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SKILL, "../s", json("{\"files\":{\"a\":\"x\"}}"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMarkersReservedNamesAndGitPaths() {
        var service = service();
        var id = service.create("P", "").id();

        for (var markdown : List.of("a <!-- cc-manager:begin x --> b", "<!-- cc-manager:end style -->")) {
            assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.INSTRUCTION, "Style", JSON.createObjectNode().put("markdown", markdown))).as(markdown).hasMessageContaining("markers");
        }
        for (var path : List.of("CON", "docs/aux.txt", "lpt1.md", "dir./x", ".git/config", "a/.Git/hooks/x", "d /x")) {
            var payload = JSON.createObjectNode().put("path", path).put("content", "c");
            assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.FILE, "f", payload)).as(path).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.AGENT, "nul", json("{\"content\":\"x\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SKILL, "com1", json("{\"files\":{\"SKILL.md\":\"x\"}}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SKILL, "ok", json("{\"files\":{\"prn.md\":\"x\"}}"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletedBundledNameCanBeTakenByAUserPresetAndRestoreThenAsksForARename() {
        var service = service(web(1, instruction("Rule", "a")));
        service.delete(service.list().getFirst().id());

        var mine = service.create("Web", "mine");

        assertThat(service.list()).extracting(ProjectPreset::name).containsExactly("Web");
        assertThatThrownBy(() -> service.restore("web")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rename it first");
        service.restoreAll();
        assertThat(service.list()).singleElement().satisfies(p -> assertThat(p.id()).isEqualTo(mine.id()));

        service.update(mine.id(), "Web mine", "mine");
        service.restore("web");
        assertThat(service.list()).extracting(ProjectPreset::name).containsExactlyInAnyOrder("Web", "Web mine");
    }

    @Test
    void corruptComponentIsSkippedAndCorruptBundledPresetDoesNotAbortStartup() {
        var service = service(web(1, instruction("Rule", "a")));
        var preset = service.list().getFirst();
        service.addComponent(preset.id(), PresetComponentKind.INSTRUCTION, "Other", json("{\"markdown\":\"b\"}"));
        jdbc.sql("UPDATE project_preset_component SET payload = '{broken' WHERE name = 'Rule'").update();

        assertThat(service.get(preset.id()).orElseThrow().components()).extracting(PresetComponent::name).containsExactly("Other");

        var broken = new BundledPreset("bad", 1, "Bad", "", List.of(new PresetComponentSpec(PresetComponentKind.FILE, "f", JSON.createObjectNode())));
        assertThat(service(web(1), broken).list()).extracting(ProjectPreset::name).containsExactly("Web");
    }

    @Test
    void movingTheOnlyComponentChangesNothing() {
        var service = service();
        var preset = service.create("P", "");
        var component = service.addComponent(preset.id(), PresetComponentKind.INSTRUCTION, "A", json("{\"markdown\":\"x\"}"));
        var presetOf = jdbc.sql("SELECT preset_id FROM project_preset_component WHERE id = ?").param(component.id()).query(Long.class).single();
        assertThat(presetOf).isEqualTo(preset.id());

        service.moveComponent(component.id(), true);
        service.moveComponent(component.id(), false);

        assertThat(service.get(preset.id()).orElseThrow().components()).hasSize(1);
    }

    @Test
    void normalisesFilePath() {
        var service = service();
        var id = service.create("P", "").id();

        var added = service.addComponent(id, PresetComponentKind.FILE, "f", json("{\"path\":\"./a\\\\b//c.json\",\"content\":\"\"}"));

        assertThat(added.payload().path("path").asString()).isEqualTo("a/b/c.json");
    }

    @Test
    void bundledPresetBecomesEditedAndRestoreResetsIt() {
        var service = service(web(1, instruction("Rule", "a")));
        var preset = service.list().getFirst();
        assertThat(preset.edited()).isFalse();

        service.addComponent(preset.id(), PresetComponentKind.INSTRUCTION, "Extra", json("{\"markdown\":\"b\"}"));
        var edited = service.get(preset.id()).orElseThrow();
        assertThat(edited.edited()).isTrue();
        assertThat(edited.revision()).isEqualTo(2);

        service.restore("web");
        var restored = service.get(preset.id()).orElseThrow();
        assertThat(restored.edited()).isFalse();
        assertThat(restored.components()).extracting(PresetComponent::name).containsExactly("Rule");
        assertThat(restored.revision()).isEqualTo(3);
    }

    @Test
    void updateMarksBundledPresetEdited() {
        var service = service(web(1, instruction("Rule", "a")));

        assertThat(service.update(service.list().getFirst().id(), "Web 2", "x").edited()).isTrue();
    }

    @Test
    void deletedBundledPresetIsNotSeededAgainButRestoreBringsItBack() {
        var service = service(web(1, instruction("Rule", "a")));
        service.delete(service.list().getFirst().id());

        service.syncBundled();
        assertThat(service.list()).isEmpty();

        service.restoreAll();
        assertThat(service.list()).extracting(ProjectPreset::name).containsExactly("Web");
    }

    @Test
    void uneditedPresetIsUpgradedSilentlyAndEditedOneIsFlagged() {
        service(web(1, instruction("Rule", "a")));
        var upgraded = service(web(2, instruction("Rule", "a"), instruction("New", "z"))).list().getFirst();
        assertThat(upgraded.components()).hasSize(2);
        assertThat(upgraded.edited()).isFalse();
        assertThat(upgraded.defaultChanged()).isFalse();

        var service = service(web(2, instruction("Rule", "a"), instruction("New", "z")));
        service.addComponent(upgraded.id(), PresetComponentKind.INSTRUCTION, "Mine", json("{\"markdown\":\"m\"}"));
        var flagged = service(web(3, instruction("Rule", "a"))).list().getFirst();

        assertThat(flagged.defaultChanged()).isTrue();
        assertThat(flagged.components()).hasSize(3);
    }

    @Test
    void nameConflictSkipsBundledPresetInsteadOfFailing() {
        var service = service();
        service.create("Web", "mine");

        var presets = service(web(1, instruction("Rule", "a"))).list();

        assertThat(presets).singleElement().satisfies(p -> assertThat(p.bundledKey()).isNull());
    }

    @Test
    void failedBundledInsertRollsBackTheWholePreset() {
        var broken = web(1, instruction("Rule", "a"), instruction("Rule", "b"));

        var service = service(broken);

        assertThat(service.list()).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM project_preset").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM project_preset_component").query(Long.class).single()).isZero();
    }

    @Test
    void listPutsBundledInShippedOrderBeforeUserPresets() {
        var service = service(new BundledPreset("zeta", 1, "Zeta", "", List.of(instruction("R", "a"))), new BundledPreset("alpha", 1, "Alpha", "", List.of(instruction("R", "a"))));
        service.create("Aaa user", "");

        assertThat(service.list()).extracting(ProjectPreset::name).containsExactly("Zeta", "Alpha", "Aaa user");
    }

    @Test
    void listenersFireOnChangeUntilUnsubscribed() {
        var service = service();
        var fired = new AtomicInteger();
        var unsubscribe = service.subscribe(fired::incrementAndGet);

        service.create("A", "");
        unsubscribe.run();
        service.create("B", "");

        assertThat(fired).hasValue(1);
    }

    @Test
    void applicationsTrackOutdatedRevisionsAndDieWithTheProject() {
        var service = service();
        var applications = new PresetApplicationService(jdbc);
        var preset = service.create("P", "");
        var project = project("p1");

        applications.markApplied(project, preset.id(), preset.revision());
        assertThat(applications.applications(project)).containsEntry(preset.id(), 1);
        assertThat(applications.outdated(project)).isEmpty();

        service.addComponent(preset.id(), PresetComponentKind.INSTRUCTION, "R", json("{\"markdown\":\"x\"}"));
        assertThat(applications.outdated(project)).containsExactly(preset.id());

        applications.markApplied(project, preset.id(), 2);
        assertThat(applications.outdated(project)).isEmpty();

        applications.declineOffer(project);
        applications.declineOffer(project);
        assertThat(applications.offerDeclined(project)).isTrue();

        jdbc.sql("DELETE FROM project WHERE id = ?").param(project).update();
        assertThat(applications.applications(project)).isEmpty();
        assertThat(applications.offerDeclined(project)).isFalse();
    }

    @Test
    void deletingPresetRemovesItsApplicationsAndComponents() {
        var service = service();
        var applications = new PresetApplicationService(jdbc);
        var preset = service.create("P", "");
        service.addComponent(preset.id(), PresetComponentKind.INSTRUCTION, "R", json("{\"markdown\":\"x\"}"));
        var project = project("p1");
        applications.markApplied(project, preset.id(), 1);

        service.delete(preset.id());

        assertThat(applications.applications(project)).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM project_preset_component").query(Long.class).single()).isZero();
    }

    private static List<String> namesOfKind(ProjectPresetService service, long id, PresetComponentKind kind) {
        return service.get(id).orElseThrow().components().stream().filter(c -> c.kind() == kind).map(PresetComponent::name).toList();
    }

    @Test
    void movingSwapsOnlyWithTheSameKind() {
        var service = service();
        var id = service.create("Mixed", "").id();
        var a = service.addComponent(id, PresetComponentKind.INSTRUCTION, "A", json("{\"markdown\":\"a\"}"));
        service.addComponent(id, PresetComponentKind.SKILL, "S", json("{\"files\":{\"SKILL.md\":\"s\"}}"));
        var b = service.addComponent(id, PresetComponentKind.INSTRUCTION, "B", json("{\"markdown\":\"b\"}"));

        service.moveComponent(b.id(), true);
        assertThat(namesOfKind(service, id, PresetComponentKind.INSTRUCTION)).containsExactly("B", "A");
        service.moveComponent(b.id(), true);
        assertThat(namesOfKind(service, id, PresetComponentKind.INSTRUCTION)).containsExactly("B", "A");
        service.moveComponent(b.id(), false);
        assertThat(namesOfKind(service, id, PresetComponentKind.INSTRUCTION)).containsExactly("A", "B");
        service.moveComponent(b.id(), false);
        assertThat(namesOfKind(service, id, PresetComponentKind.INSTRUCTION)).containsExactly("A", "B");
        assertThat(service.findComponent(a.id())).isPresent();
        assertThat(service.findComponent(-5)).isEmpty();
    }

    @Test
    void movingAPluginSkipsMarketplaces() {
        var service = service();
        var id = service.create("Plugins", "").id();
        service.addComponent(id, PresetComponentKind.PLUGIN, "one@m", json("{}"));
        service.addComponent(id, PresetComponentKind.MARKETPLACE, "m", json("{\"source\":\"o/m\"}"));
        var two = service.addComponent(id, PresetComponentKind.PLUGIN, "two@m", json("{}"));

        service.moveComponent(two.id(), true);

        assertThat(namesOfKind(service, id, PresetComponentKind.PLUGIN)).containsExactly("two@m", "one@m");
        assertThat(namesOfKind(service, id, PresetComponentKind.MARKETPLACE)).containsExactly("m");
    }

    @Test
    void rejectsTooManySkillFilesAndOversizedContent() {
        var service = service();
        var id = service.create("Big", "").id();
        var files = JSON.createObjectNode();
        for (var i = 0; i <= PresetComponentValidator.MAX_SKILL_FILES; i++) {
            files.put("f" + i + ".md", "x");
        }
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.SKILL, "many", JSON.createObjectNode().set("files", files)))
                .hasMessageContaining("at most");

        var huge = "x".repeat(PresetComponentValidator.MAX_PAYLOAD_BYTES + 1);
        assertThatThrownBy(() -> service.addComponent(id, PresetComponentKind.INSTRUCTION, "huge", JSON.createObjectNode().put("markdown", huge)))
                .hasMessageContaining("larger than");
    }
}
