package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.extension.plugin.ShownCommand;
import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset;
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PresetApplyServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    Path project;

    private final ProjectPresetService presets = mock(ProjectPresetService.class);
    private final PresetApplicationService applications = mock(PresetApplicationService.class);
    private final PluginService plugins = mock(PluginService.class);
    private final McpService mcp = mock(McpService.class);
    private PresetApplyService service;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        service = new PresetApplyService(presets, applications, List.of(new MarketplaceApplier(plugins), new PluginApplier(plugins),
                new McpServerApplier(mcp), new SkillApplier(), new AgentApplier(), new FileApplier(), new InstructionApplier(), new SettingsApplier()));
        when(plugins.install(anyString(), any(), any())).thenReturn(new PluginInstallResult(true, "", null));
    }

    @Test
    void everyKindIsPlannedAndAppliedThenSecondApplyIsAllPresent() throws IOException {
        var components = List.of(
                component(PresetComponentKind.SETTINGS, "s", "{\"fragment\":{\"a\":1}}"),
                component(PresetComponentKind.INSTRUCTION, "Style", "{\"markdown\":\"Use tabs.\"}"),
                component(PresetComponentKind.FILE, "f", "{\"path\":\"docs/x.txt\",\"content\":\"hi\",\"gitignore\":[\"build/\",\"build/\"]}"),
                component(PresetComponentKind.AGENT, "reviewer", "{\"content\":\"agent text\"}"),
                component(PresetComponentKind.SKILL, "tidy", "{\"files\":{\"SKILL.md\":\"skill\",\"ref/a.md\":\"a\"}}"));
        var ids = preset(components);

        assertThat(service.plan(1, project)).extracting(PlannedChange::action).containsOnly(ChangeAction.CREATE);
        var result = service.apply(7, 1, project, ids);

        assertThat(result.outcomes()).extracting(ComponentOutcome::status).containsOnly(OutcomeStatus.APPLIED);
        assertThat(result.outcomes().getLast().kind()).isEqualTo(PresetComponentKind.SETTINGS);
        assertThat(project.resolve(".claude/skills/tidy/ref/a.md")).hasContent("a");
        assertThat(project.resolve(".claude/agents/reviewer.md")).hasContent("agent text");
        assertThat(project.resolve("docs/x.txt")).hasContent("hi");
        assertThat(project.resolve(".gitignore")).hasContent("build/\n");
        assertThat(project.resolve("CLAUDE.md")).content().contains("## Style\nUse tabs.");
        assertThat(project.resolve(".claude/settings.json")).content().contains("\"a\"");
        verify(applications).markApplied(7, 1, 3);

        assertThat(service.plan(1, project)).extracting(PlannedChange::action).containsOnly(ChangeAction.PRESENT);
        var again = service.apply(7, 1, project, ids);
        assertThat(again.outcomes()).extracting(ComponentOutcome::status).containsOnly(OutcomeStatus.SKIPPED);
        assertThat(again.recorded()).isTrue();
    }

    @Test
    void conflictsAreNeverWritten() throws IOException {
        Files.createDirectories(project.resolve(".claude/agents"));
        Files.writeString(project.resolve(".claude/agents/reviewer.md"), "mine");
        Files.writeString(project.resolve("x.txt"), "mine");
        Files.writeString(project.resolve("CLAUDE.md"), "## Style\nmine\n");
        Files.writeString(project.resolve(".claude/settings.json"), "{\"a\":2}");
        Files.createDirectories(project.resolve(".claude/skills/tidy"));
        Files.writeString(project.resolve(".claude/skills/tidy/SKILL.md"), "mine");
        var ids = preset(List.of(
                component(PresetComponentKind.AGENT, "reviewer", "{\"content\":\"theirs\"}"),
                component(PresetComponentKind.FILE, "f", "{\"path\":\"x.txt\",\"content\":\"theirs\"}"),
                component(PresetComponentKind.INSTRUCTION, "Style", "{\"markdown\":\"theirs\"}"),
                component(PresetComponentKind.SETTINGS, "s", "{\"fragment\":{\"a\":1}}"),
                component(PresetComponentKind.SKILL, "tidy", "{\"files\":{\"SKILL.md\":\"theirs\"}}")));

        assertThat(service.plan(1, project)).extracting(PlannedChange::action).containsOnly(ChangeAction.CONFLICT);
        var result = service.apply(7, 1, project, ids);

        assertThat(result.outcomes()).extracting(ComponentOutcome::status).containsOnly(OutcomeStatus.SKIPPED);
        assertThat(result.recorded()).isFalse();
        assertThat(project.resolve(".claude/agents/reviewer.md")).hasContent("mine");
        assertThat(project.resolve("x.txt")).hasContent("mine");
        assertThat(project.resolve("CLAUDE.md")).hasContent("## Style\nmine\n");
        assertThat(project.resolve(".claude/settings.json")).hasContent("{\"a\":2}");
        assertThat(project.resolve(".claude/skills/tidy/SKILL.md")).hasContent("mine");
        verify(applications, never()).markApplied(eq(7L), eq(1L), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void settingsMergePartiallyAndReportTheDifferingKey() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".claude/settings.json"), "{\"a\":2}");
        var ids = preset(List.of(component(PresetComponentKind.SETTINGS, "s", "{\"fragment\":{\"a\":1,\"b\":true}}")));

        var change = service.plan(1, project).getFirst();
        var result = service.apply(7, 1, project, ids);

        assertThat(change.action()).isEqualTo(ChangeAction.UPDATE);
        assertThat(change.detail()).contains("a");
        assertThat(result.outcomes().getFirst().status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(project.resolve(".claude/settings.json")).content().contains("\"a\" : 2").contains("\"b\" : true");
    }

    @Test
    void escapingFilePathIsAConflictAndNothingIsWritten() {
        var ids = preset(List.of(
                component(PresetComponentKind.FILE, "up", "{\"path\":\"../evil.txt\",\"content\":\"x\"}"),
                component(PresetComponentKind.FILE, "abs", "{\"path\":\"/evil.txt\",\"content\":\"x\"}")));

        assertThat(service.plan(1, project)).extracting(PlannedChange::action).containsOnly(ChangeAction.CONFLICT);
        service.apply(7, 1, project, ids);

        assertThat(project.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    void crlfFilesKeepTheirLineEndings() throws IOException {
        Files.writeString(project.resolve("CLAUDE.md"), "# Mine\r\n");
        Files.writeString(project.resolve(".gitignore"), "bin\r\n");
        var ids = preset(List.of(
                component(PresetComponentKind.INSTRUCTION, "Style", "{\"markdown\":\"a\\nb\"}"),
                component(PresetComponentKind.FILE, "f", "{\"path\":\"x\",\"content\":\"c\",\"gitignore\":[\"obj/\"]}")));

        service.apply(7, 1, project, ids);

        assertThat(Files.readString(project.resolve("CLAUDE.md"))).contains("a\r\nb\r\n").doesNotContainPattern("[^\\r]\\n");
        assertThat(Files.readString(project.resolve(".gitignore"))).isEqualTo("bin\r\nobj/\r\n");
    }

    @Test
    void oneFailingComponentDoesNotStopTheOthers() {
        doThrow(new ExtensionException("boom")).when(plugins).addMarketplace(anyString(), any(), any());
        var ids = preset(List.of(
                component(PresetComponentKind.MARKETPLACE, "m", "{\"source\":\"org/repo\"}"),
                component(PresetComponentKind.AGENT, "reviewer", "{\"content\":\"t\"}")));

        var result = service.apply(7, 1, project, ids);

        assertThat(result.outcomes()).extracting(ComponentOutcome::status).containsExactly(OutcomeStatus.FAILED, OutcomeStatus.APPLIED);
        assertThat(result.outcomes().getFirst().message()).isEqualTo("boom");
        assertThat(result.recorded()).isFalse();
        verify(applications, never()).markApplied(eq(7L), eq(1L), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void claudeMdSymlinkStaysALinkAndItsTargetIsRewritten() throws IOException {
        Files.writeString(project.resolve("AGENTS.md"), "# Mine\n");
        try {
            Files.createSymbolicLink(project.resolve("CLAUDE.md"), Path.of("AGENTS.md"));
        } catch (IOException | UnsupportedOperationException e) {
            org.assertj.core.api.Assumptions.assumeThat(false).as("symlinks cannot be created here").isTrue();
        }
        var ids = preset(List.of(component(PresetComponentKind.INSTRUCTION, "Style", "{\"markdown\":\"Use tabs.\"}")));

        service.apply(7, 1, project, ids);

        assertThat(Files.isSymbolicLink(project.resolve("CLAUDE.md"))).isTrue();
        assertThat(project.resolve("AGENTS.md")).content().startsWith("# Mine").contains("## Style\nUse tabs.");
    }

    @Test
    void mcpPlanWarnsAboutTheCommittedFileWithoutShowingTheConfig() {
        var ids = preset(List.of(component(PresetComponentKind.MCP_SERVER, "srv", "{\"config\":{\"url\":\"https://x\",\"token\":\"s3cret\"}}")));

        var change = service.plan(1, project).getFirst();

        assertThat(change.detail()).startsWith("Written to .mcp.json in the project, which is usually committed: check it contains no secrets.")
                .contains("srv").doesNotContain("s3cret");
        assertThat(ids).hasSize(1);
    }

    @Test
    void sameProjectIsAppliedOneAtATime() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            release.await();
            return null;
        }).when(plugins).addMarketplace(anyString(), any(), any());
        var ids = preset(List.of(component(PresetComponentKind.MARKETPLACE, "m", "{\"source\":\"o/r\"}")));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.apply(7, 1, project, ids));
            entered.await();
            var second = pool.submit(() -> service.apply(7, 1, project, ids));
            Thread.sleep(200);
            assertThat(calls).hasValue(1);
            release.countDown();
            first.get();
            second.get();
            assertThat(calls).hasValue(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void skillFileKeysAreNormalisedBeforeWriting() {
        var ids = preset(List.of(component(PresetComponentKind.SKILL, "tidy", "{\"files\":{\"SKILL.md\":\"s\",\"ref\\\\a.md\":\"a\"}}")));

        service.apply(7, 1, project, ids);

        assertThat(project.resolve(".claude/skills/tidy/ref/a.md")).hasContent("a");
    }

    @Test
    void reservedAndGitPathsAreConflicts() {
        preset(List.of(
                component(PresetComponentKind.FILE, "a", "{\"path\":\"docs/NUL.txt\",\"content\":\"x\"}"),
                component(PresetComponentKind.FILE, "b", "{\"path\":\".GIT/hooks/pre-commit\",\"content\":\"x\"}"),
                component(PresetComponentKind.FILE, "c", "{\"path\":\"dir./x\",\"content\":\"x\"}")));

        assertThat(service.plan(1, project)).extracting(PlannedChange::action).containsOnly(ChangeAction.CONFLICT);
    }

    @Test
    void unselectedComponentsAreUntouched() {
        var all = preset(List.of(
                component(PresetComponentKind.AGENT, "a", "{\"content\":\"t\"}"),
                component(PresetComponentKind.AGENT, "b", "{\"content\":\"t\"}")));

        var result = service.apply(7, 1, project, Set.of(all.iterator().next()));

        assertThat(result.outcomes()).extracting(ComponentOutcome::status).containsExactlyInAnyOrder(OutcomeStatus.APPLIED, OutcomeStatus.SKIPPED);
        assertThat(project.resolve(".claude/agents").toFile().list()).hasSize(1);
    }

    @Test
    void pluginNeedingConfirmationAndMcpSignInAreReported() {
        when(plugins.install(eq("p@m"), eq(ExtensionScope.PROJECT), eq(project)))
                .thenReturn(new PluginInstallResult(false, "", new ShownCommand("cmd", "sha")));
        when(mcp.health(project)).thenReturn(List.of(new McpHealth("srv", "", McpStatus.NEEDS_AUTHENTICATION, "")));
        var ids = preset(List.of(
                component(PresetComponentKind.PLUGIN, "p@m", "{}"),
                component(PresetComponentKind.MCP_SERVER, "srv", "{\"config\":{\"url\":\"https://x\"}}")));

        var result = service.apply(7, 1, project, ids);

        assertThat(result.outcomes()).extracting(ComponentOutcome::status).containsExactly(OutcomeStatus.NEEDS_CONFIRMATION, OutcomeStatus.SIGN_IN_NEEDED);
        verify(mcp).add("srv", "{\"url\":\"https://x\"}", ExtensionScope.PROJECT, project);
    }

    @Test
    void extensionsAreAppliedBeforeFilesAndSettingsLast() {
        var ids = preset(List.of(
                component(PresetComponentKind.SETTINGS, "s", "{\"fragment\":{\"a\":1}}"),
                component(PresetComponentKind.INSTRUCTION, "Style", "{\"markdown\":\"x\"}"),
                component(PresetComponentKind.AGENT, "a", "{\"content\":\"t\"}"),
                component(PresetComponentKind.MCP_SERVER, "srv", "{\"config\":\"{}\"}"),
                component(PresetComponentKind.PLUGIN, "p@m", "{}"),
                component(PresetComponentKind.MARKETPLACE, "m", "{\"source\":\"o/r\"}")));

        var kinds = service.apply(7, 1, project, ids).outcomes().stream().map(ComponentOutcome::kind).toList();

        assertThat(kinds).containsExactly(PresetComponentKind.MARKETPLACE, PresetComponentKind.PLUGIN, PresetComponentKind.MCP_SERVER,
                PresetComponentKind.AGENT, PresetComponentKind.INSTRUCTION, PresetComponentKind.SETTINGS);
    }

    private Set<Long> preset(List<PresetComponent> components) {
        when(presets.get(1L)).thenReturn(Optional.of(new ProjectPreset(1, "p", "", null, false, false, 3, components)));
        return components.stream().map(PresetComponent::id).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private PresetComponent component(PresetComponentKind kind, String name, String payload) {
        var node = json(payload);
        return new PresetComponent(nextId++, kind, name, node);
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }
}
