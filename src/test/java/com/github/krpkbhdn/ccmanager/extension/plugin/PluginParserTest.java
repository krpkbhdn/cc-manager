package com.github.krpkbhdn.ccmanager.extension.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginParserTest {

    @Test
    void parsesInstalledPluginsOfEveryScope() {
        var json = """
                [{"id":"a@m","version":"1.0.0","scope":"user","enabled":true,"installPath":"C:\\\\x\\\\a","extra":1},
                 {"id":"b@m","version":"2","scope":"project","enabled":false,"installPath":"/b","projectPath":"/work/p"},
                 {"id":"c@m","scope":"managed","installPath":"/c"}]""";

        var plugins = PluginParser.installed(json);

        assertThat(plugins).hasSize(2);
        assertThat(plugins.get(0)).isEqualTo(new InstalledPlugin("a@m", "1.0.0", ExtensionScope.USER, true, Path.of("C:\\x\\a"), null));
        assertThat(plugins.get(1).projectPath()).isEqualTo(Path.of("/work/p"));
        assertThat(plugins.get(1).enabled()).isFalse();
    }

    @Test
    void parsesAvailablePluginsAndIgnoresInstalledSection() {
        var json = """
                {"installed":[{"id":"a@m","scope":"user"}],
                 "available":[{"pluginId":"x@m","name":"x","description":"d","marketplaceName":"m","source":{"a":1},"installCount":7},
                              {"name":"no id"}]}""";

        assertThat(PluginParser.available(json))
                .containsExactly(new AvailablePlugin("x@m", "x", "d", "m", 7));
    }

    @Test
    void parsesMarketplacesOfAnySourceKind() {
        var json = """
                [{"name":"gh","source":"github","repo":"o/r","installLocation":"/l"},
                 {"name":"web","source":"url","url":"https://x/y.json","installLocation":"/w"},
                 {"name":"local","source":"directory","path":"/p","installLocation":"/p"}]""";

        assertThat(PluginParser.marketplaces(json)).extracting(Marketplace::reference)
                .containsExactly("o/r", "https://x/y.json", "/p");
    }

    @Test
    void parsesDetailsComponentsAndAlwaysOnTokens() {
        var text = """
                claude-md-management 1.0.0
                  Description: Tools to maintain CLAUDE.md files
                  Source: claude-md-management@claude-plugins-official

                Component inventory
                  Skills (2)  claude-md-improver, revise-claude-md
                  Agents (0)
                  MCP servers (1)  github

                Projected token cost
                  Always-on:   ~1.5k tok   added to every session
                """;

        var details = PluginParser.details(text);

        assertThat(details.description()).isEqualTo("Tools to maintain CLAUDE.md files");
        assertThat(details.alwaysOnTokens()).isEqualTo(1500);
        assertThat(details.components()).containsExactly(
                new PluginComponent("Skills", 2, java.util.List.of("claude-md-improver", "revise-claude-md")),
                new PluginComponent("Agents", 0, java.util.List.of()),
                new PluginComponent("MCP servers", 1, java.util.List.of("github")));
    }

    @Test
    void installResultCarriesTheCommandAwaitingConfirmation() {
        var stdout = "noise\n{\"success\":false,\"shownCommand\":{\"command\":\"npx setup\",\"sha256\":\"abc\"}}\n";

        var result = PluginParser.installResult(1, stdout, "");

        assertThat(result.installed()).isFalse();
        assertThat(result.needsConfirmation()).isTrue();
        assertThat(result.pendingCommand()).isEqualTo(new ShownCommand("npx setup", "abc"));
    }

    @Test
    void detailsDropTheTrailingNoteOfAComponentLine() {
        var text = "Component inventory\n  Hooks (3)  SessionStart, SubagentStart  (harness-only — no model context cost)\n";

        assertThat(PluginParser.details(text).components()).containsExactly(
                new PluginComponent("Hooks", 3, java.util.List.of("SessionStart", "SubagentStart")));
    }

    @Test
    void installResultReadsPrettyPrintedJsonSpreadOverLines() {
        var stdout = "{\n  \"shownCommand\": {\n    \"command\": \"npx x\",\n    \"sha256\": \"abc\"\n  }\n}\n";

        assertThat(PluginParser.installResult(1, stdout, "").pendingCommand()).isEqualTo(new ShownCommand("npx x", "abc"));
    }

    @Test
    void installResultOfSuccessHasNoPendingCommand() {
        var result = PluginParser.installResult(0, "{\"message\":\"Installed\"}", "");

        assertThat(result.installed()).isTrue();
        assertThat(result.needsConfirmation()).isFalse();
        assertThat(result.message()).isEqualTo("Installed");
    }

    @Test
    void installResultOfPlainFailureKeepsStderr() {
        var result = PluginParser.installResult(1, "", "Plugin not found\n");

        assertThat(result.installed()).isFalse();
        assertThat(result.message()).isEqualTo("Plugin not found");
    }
}
