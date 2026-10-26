package com.github.krpkbhdn.ccmanager.author.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.author.AuthorProfileLoader;
import com.github.krpkbhdn.ccmanager.author.ExtensionSet;
import com.github.krpkbhdn.ccmanager.author.ProfileMarketplace;
import com.github.krpkbhdn.ccmanager.author.ProfileMcpServer;
import com.github.krpkbhdn.ccmanager.author.ProfileSkill;
import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus;
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.extension.plugin.ShownCommand;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ExtensionInstallerTest {

    final PluginService plugins = mock(PluginService.class);
    final McpService mcp = mock(McpService.class);
    final SkillService skills = mock(SkillService.class);
    final ExtensionInstaller installer = new ExtensionInstaller(plugins, mcp, skills, new AuthorProfileLoader());

    @TempDir
    Path temp;

    ExtensionSet pluginSet(String... ids) {
        return new ExtensionSet(null, List.of(ids), null, null);
    }

    @Test
    void installsMissingMarketplaceAndSkipsKnownOne() {
        when(plugins.marketplaces()).thenReturn(List.of(new Marketplace("known", "github", "a/b", "x")));
        var set = new ExtensionSet(List.of(new ProfileMarketplace("known", "a/b"), new ProfileMarketplace("fresh", "c/d")), null, null, null);

        var outcome = installer.install(set, ExtensionScope.USER, null);

        assertThat(outcome.installed()).containsExactly("marketplace fresh");
        assertThat(outcome.alreadyPresent()).containsExactly("marketplace known");
        verify(plugins, never()).addMarketplace(eq("a/b"), any(), any());
    }

    @Test
    void installedPluginIsSkippedAndConfirmationIsNeverAccepted() {
        when(plugins.installed()).thenReturn(List.of(new InstalledPlugin("have@m", "1", ExtensionScope.USER, true, temp, null)));
        when(plugins.install("ask@m", ExtensionScope.USER, null))
                .thenReturn(new PluginInstallResult(false, "confirm", new ShownCommand("cmd", "sha")));
        when(plugins.install("new@m", ExtensionScope.USER, null)).thenReturn(new PluginInstallResult(true, "ok", null));

        var outcome = installer.install(pluginSet("have@m", "ask@m", "new@m"), ExtensionScope.USER, null);

        assertThat(outcome.alreadyPresent()).containsExactly("plugin have@m");
        assertThat(outcome.needsAttention()).containsExactly("plugin ask@m");
        assertThat(outcome.installed()).containsExactly("plugin new@m");
        verify(plugins, never()).installAccepting(anyString(), any(), any(), any());
    }

    @Test
    void failingPluginDoesNotStopTheRest() {
        when(plugins.install("bad@m", ExtensionScope.USER, null)).thenThrow(new ExtensionException("boom"));
        when(plugins.install("good@m", ExtensionScope.USER, null)).thenReturn(new PluginInstallResult(true, "ok", null));

        var outcome = installer.install(pluginSet("bad@m", "good@m"), ExtensionScope.USER, null);

        assertThat(outcome.failed()).containsExactly("plugin bad@m: boom");
        assertThat(outcome.installed()).containsExactly("plugin good@m");
        assertThat(outcome.hasFailures()).isTrue();
    }

    @Test
    void copiesMissingSkillAndNeverOverwritesAnExistingOne() throws IOException {
        var fresh = temp.resolve("skills/jscpd");
        var existing = Files.createDirectories(temp.resolve("skills/dry-refactoring"));
        Files.writeString(existing.resolve("SKILL.md"), "mine");
        when(skills.skillDirectory(ExtensionScope.USER, null, "jscpd")).thenReturn(fresh);
        when(skills.skillDirectory(ExtensionScope.USER, null, "dry-refactoring")).thenReturn(existing);
        var set = new ExtensionSet(null, null, List.of(new ProfileSkill("jscpd", List.of("SKILL.md")),
                new ProfileSkill("dry-refactoring", List.of("SKILL.md"))), null);

        var outcome = installer.install(set, ExtensionScope.USER, null);

        assertThat(fresh.resolve("SKILL.md")).isRegularFile();
        assertThat(existing.resolve("SKILL.md")).hasContent("mine");
        assertThat(outcome.installed()).containsExactly("skill jscpd");
        assertThat(outcome.alreadyPresent()).containsExactly("skill dry-refactoring");
    }

    @Test
    void healthIsNotQueriedWithoutMcpServers() {
        installer.install(pluginSet(), ExtensionScope.USER, null);

        verify(mcp, never()).health(any());
    }

    @Test
    void declaredMcpServerNeedingSignInIsReported() {
        var config = JsonMapper.builder().build().readTree("{\"type\":\"http\",\"url\":\"https://x\"}");
        when(mcp.health(null)).thenReturn(List.of(new McpHealth("srv", "https://x", McpStatus.NEEDS_AUTHENTICATION, ""),
                new McpHealth("other", "y", McpStatus.NEEDS_AUTHENTICATION, "")));
        var set = new ExtensionSet(null, null, null, List.of(new ProfileMcpServer("srv", config)));

        var outcome = installer.install(set, ExtensionScope.USER, null);

        verify(mcp).add("srv", config.toString(), ExtensionScope.USER, null);
        assertThat(outcome.installed()).containsExactly("MCP server srv");
        assertThat(outcome.signInNeeded()).containsExactly("srv");
    }

    @Test
    void failedSkillCopyLeavesNoHalfCopiedSkillBehind() throws IOException {
        var target = temp.resolve("skills/jscpd");
        when(skills.skillDirectory(ExtensionScope.USER, null, "jscpd")).thenReturn(target);
        var set = new ExtensionSet(null, null, List.of(new ProfileSkill("jscpd", List.of("SKILL.md", "missing.md"))), null);

        var outcome = installer.install(set, ExtensionScope.USER, null);

        assertThat(outcome.failed()).hasSize(1);
        assertThat(target).doesNotExist();
        try (var left = Files.list(temp.resolve("skills"))) {
            assertThat(left).isEmpty();
        }
    }
}
