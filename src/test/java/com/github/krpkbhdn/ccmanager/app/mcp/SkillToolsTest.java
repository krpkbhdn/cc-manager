package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.app.AssistantTextLimitProperties;
import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.skill.Skill;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SkillToolsTest {

    private static final AssistantTextLimitProperties LIMITS = new AssistantTextLimitProperties(10);
    private static final Path DIRECTORY = Path.of("demo");
    private static final Skill OWN = new Skill("review", "Reviews", ExtensionScope.PROJECT, "", DIRECTORY.resolve("review/SKILL.md"));
    private static final Skill PLUGIN = new Skill("review", "Plugin", ExtensionScope.USER, "p@m", Path.of("p/review/SKILL.md"));

    private final ProjectService projects = mock(ProjectService.class);
    private final SkillService skills = mock(SkillService.class);

    private static JsonNode json(String text) {
        return JsonMapper.shared().readTree(text);
    }

    private void withProject() {
        when(projects.find(7)).thenReturn(Optional.of(new Project(7, "demo", DIRECTORY, Instant.EPOCH)));
    }

    @Test
    void readOnlyToolsAreAutoApprovedAndWritersAskThePermissionDialog() {
        assertThat(new ListSkillsTool(projects, skills).autoApproved()).isTrue();
        assertThat(new GetSkillTool(projects, skills, LIMITS).autoApproved()).isTrue();
        assertThat(new SetSkillTool(projects, skills).autoApproved()).isFalse();
        assertThat(new DeleteSkillTool(projects, skills).autoApproved()).isFalse();
    }

    @Test
    void listWithoutProjectAsksForUserSkillsOnly() {
        when(skills.list(null)).thenReturn(List.of(PLUGIN));

        var result = new ListSkillsTool(projects, skills).call(json("{}"));

        assertThat(result).contains("\"plugin\":\"p@m\"").contains("\"readOnly\":true");
    }

    @Test
    void getReturnsTheContentOfTheScopedSkill() {
        withProject();
        when(skills.list(DIRECTORY)).thenReturn(List.of(PLUGIN, OWN));
        when(skills.read(OWN)).thenReturn("# review");

        var result = new GetSkillTool(projects, skills, LIMITS).call(json("{\"name\":\"review\",\"project\":7,\"scope\":\"project\"}"));

        assertThat(result).isEqualTo("# review");
    }

    @Test
    void getRejectsAnUnknownSkillAndAnUnknownScope() {
        when(skills.list(null)).thenReturn(List.of());
        var tool = new GetSkillTool(projects, skills, LIMITS);

        assertThatThrownBy(() -> tool.call(json("{\"name\":\"nope\"}"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> tool.call(json("{\"name\":\"a\",\"scope\":\"global\"}"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setUpdatesAnExistingOwnSkillAndIgnoresPluginSkillsOfTheSameName() {
        withProject();
        when(skills.list(DIRECTORY)).thenReturn(List.of(PLUGIN, OWN));

        var result = new SetSkillTool(projects, skills).call(json("{\"name\":\"review\",\"project\":7,\"content\":\"new\"}"));

        verify(skills).write(OWN, "new");
        verify(skills, never()).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(result).contains("updated");
    }

    @Test
    void setCreatesAMissingSkillInTheUserScopeByDefaultWithoutProject() {
        when(skills.list(null)).thenReturn(List.of());
        when(skills.create(ExtensionScope.USER, null, "fresh", "Does things")).thenReturn(
                new Skill("fresh", "Does things", ExtensionScope.USER, "", Path.of("fresh/SKILL.md")));

        var result = new SetSkillTool(projects, skills).call(json("{\"name\":\"fresh\",\"description\":\"Does things\",\"content\":\"body\"}"));

        verify(skills).write(org.mockito.ArgumentMatchers.any(Skill.class), org.mockito.ArgumentMatchers.eq("body"));
        assertThat(result).contains("created in user scope");
    }

    @Test
    void setNeedsADescriptionToCreate() {
        when(skills.list(null)).thenReturn(List.of());

        assertThatThrownBy(() -> new SetSkillTool(projects, skills).call(json("{\"name\":\"fresh\",\"content\":\"body\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("description");
    }

    @Test
    void deleteRemovesTheOwnSkillAndRefusesAPluginOnlyMatch() {
        withProject();
        when(skills.list(DIRECTORY)).thenReturn(List.of(OWN));
        var tool = new DeleteSkillTool(projects, skills);

        assertThat(tool.call(json("{\"name\":\"review\",\"project\":7}"))).contains("deleted");
        verify(skills).delete(OWN);
        assertThatThrownBy(() -> tool.call(json("{\"name\":\"review\"}"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCutsALongSkillAndSaysSo() {
        when(skills.list(null)).thenReturn(List.of(PLUGIN));
        when(skills.read(PLUGIN)).thenReturn("y".repeat(30));

        var result = new GetSkillTool(projects, skills, LIMITS).call(json("{\"name\":\"review\"}"));

        assertThat(result).startsWith("y".repeat(10)).contains("truncated", "10 of 30");
    }

    @Test
    void setDiscardsTheNewStubWhenTheWriteFails() {
        var created = new Skill("fresh", "d", ExtensionScope.USER, "", Path.of("fresh/SKILL.md"));
        when(skills.list(null)).thenReturn(List.of());
        when(skills.create(ExtensionScope.USER, null, "fresh", "d")).thenReturn(created);
        org.mockito.Mockito.doThrow(new ExtensionException("linked")).when(skills).write(created, "body");

        assertThatThrownBy(() -> new SetSkillTool(projects, skills).call(json("{\"name\":\"fresh\",\"description\":\"d\",\"content\":\"body\"}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("linked");
        verify(skills).delete(created);
    }

    @Test
    void setMatchesTheFolderNameNotTheFrontMatterName() {
        var renamed = new Skill("Pretty Name", "d", ExtensionScope.USER, "", Path.of("code-review/SKILL.md"));
        when(skills.list(null)).thenReturn(List.of(renamed));

        new SetSkillTool(projects, skills).call(json("{\"name\":\"code-review\",\"content\":\"new\"}"));

        verify(skills).write(renamed, "new");
    }

    @Test
    void setRejectsTwoFoldersOfTheSameName() {
        var first = new Skill("a", "d", ExtensionScope.USER, "", Path.of("one/dup/SKILL.md"));
        var second = new Skill("b", "d", ExtensionScope.USER, "", Path.of("two/dup/SKILL.md"));
        when(skills.list(null)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> new SetSkillTool(projects, skills).call(json("{\"name\":\"dup\",\"content\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dup");
    }

    @Test
    void anUnknownProjectIsRejected() {
        when(projects.find(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ListSkillsTool(projects, skills).call(json("{\"project\":9}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("9");
    }
}
