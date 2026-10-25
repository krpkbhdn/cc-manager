package com.github.krpkbhdn.ccmanager.extension.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillServiceTest {

    @TempDir
    Path home;

    @TempDir
    Path project;

    @TempDir
    Path pluginDirectory;

    private final PluginService plugins = mock(PluginService.class);

    private SkillService service() {
        return new SkillService(new FakeHostEnvironment(HostEnvironment.OperatingSystem.LINUX, home), plugins);
    }

    private static void skill(Path root, String directory, String content) throws IOException {
        Files.createDirectories(root.resolve(directory));
        Files.writeString(root.resolve(directory).resolve("SKILL.md"), content);
    }

    @Test
    void listsUserProjectAndEnabledPluginSkillsAndSkipsFoldersWithoutSkillFile() throws IOException {
        skill(home.resolve(".claude/skills"), "mine", "---\nname: mine\ndescription: User skill\n---\n");
        Files.createDirectories(home.resolve(".claude/skills/empty"));
        skill(project.resolve(".claude/skills"), "shared", "---\ndescription: Project skill\n---\n");
        skill(pluginDirectory.resolve("skills"), "from-plugin", "---\nname: from-plugin\ndescription: P\n---\n");
        when(plugins.installed()).thenReturn(List.of(
                new InstalledPlugin("p@m", "1", ExtensionScope.USER, true, pluginDirectory, null),
                new InstalledPlugin("off@m", "1", ExtensionScope.USER, false, pluginDirectory, null),
                new InstalledPlugin("other@m", "1", ExtensionScope.PROJECT, true, pluginDirectory, Path.of("/elsewhere"))));

        var skills = service().list(project);

        assertThat(skills).extracting(Skill::name).containsExactly("mine", "shared", "from-plugin");
        assertThat(skills.get(1).description()).isEqualTo("Project skill");
        assertThat(skills.get(2).readOnly()).isTrue();
        assertThat(skills.get(2).pluginId()).isEqualTo("p@m");
        assertThat(skills.get(0).readOnly()).isFalse();
    }

    @Test
    void createWritesATemplateThatListsBack() {
        when(plugins.installed()).thenReturn(List.of());
        var service = service();

        var created = service.create(ExtensionScope.PROJECT, project, "code-review", "Reviews\ncode carefully");

        assertThat(created.description()).isEqualTo("Reviews code carefully");
        assertThat(service.read(created)).startsWith("---\nname: code-review\n");
        assertThat(service.list(project)).extracting(Skill::name).containsExactly("code-review");
    }

    @Test
    void createValidatesNameDescriptionScopeAndDuplicates() {
        var service = service();
        service.create(ExtensionScope.USER, null, "taken", "d");

        assertThatThrownBy(() -> service.create(ExtensionScope.USER, null, "Bad_Name", "d")).isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> service.create(ExtensionScope.USER, null, "a--b", "d")).isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> service.create(ExtensionScope.USER, null, "ok", "  ")).isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> service.create(ExtensionScope.USER, null, "taken", "d")).hasMessageContaining("already exists");
        assertThatThrownBy(() -> service.create(ExtensionScope.LOCAL, project, "ok", "d")).isInstanceOf(ExtensionException.class);
    }

    @Test
    void aLinkedSkillDirectoryIsNeitherListedNorDeletedAndItsTargetSurvives() throws IOException {
        var target = project.resolve("outside/linked");
        skill(project.resolve("outside"), "linked", "---\nname: linked\ndescription: d\n---\n");
        var skillsRoot = home.resolve(".claude/skills");
        Files.createDirectories(skillsRoot);
        var link = skillsRoot.resolve("linked");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("cannot create links here");
        }
        when(plugins.installed()).thenReturn(List.of());
        var service = service();

        assertThat(service.list(null)).isEmpty();
        var linked = new Skill("linked", "d", ExtensionScope.USER, "", link.resolve("SKILL.md"));
        assertThatThrownBy(() -> service.delete(linked)).isInstanceOf(ExtensionException.class);
        assertThat(target.resolve("SKILL.md")).exists();
    }

    @Test
    void deleteRemovesTheWholeDirectoryButNeverAPluginSkill() throws IOException {
        var service = service();
        var created = service.create(ExtensionScope.USER, null, "doomed", "d");
        Files.writeString(created.directory().resolve("extra.txt"), "x");

        service.delete(created);

        assertThat(created.directory()).doesNotExist();
        var pluginSkill = new Skill("s", "d", ExtensionScope.USER, "p@m", pluginDirectory.resolve("skills/s/SKILL.md"));
        assertThatThrownBy(() -> service.delete(pluginSkill)).isInstanceOf(ExtensionException.class);
        var stray = new Skill("s", "d", ExtensionScope.USER, "", project.resolve("elsewhere/SKILL.md"));
        assertThatThrownBy(() -> service.delete(stray)).isInstanceOf(ExtensionException.class);
    }

    @Test
    void honoursClaudeConfigDirForListingCreatingAndDeletingUserSkills() {
        var configDirectory = project.resolve("custom-config");
        var host = new FakeHostEnvironment(HostEnvironment.OperatingSystem.LINUX, home).withEnv("CLAUDE_CONFIG_DIR", configDirectory.toString());
        when(plugins.installed()).thenReturn(List.of());
        var service = new SkillService(host, plugins);

        var created = service.create(ExtensionScope.USER, null, "mine", "d");

        assertThat(created.file()).isEqualTo(configDirectory.resolve("skills/mine/SKILL.md"));
        assertThat(service.list(null)).extracting(Skill::name).containsExactly("mine");
        service.delete(created);
        assertThat(created.directory()).doesNotExist();
    }
}
