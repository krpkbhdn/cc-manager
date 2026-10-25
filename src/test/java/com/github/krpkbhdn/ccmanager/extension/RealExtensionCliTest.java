package com.github.krpkbhdn.ccmanager.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService;
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Read-only calls against the real claude CLI; never installs, removes or changes anything. */
class RealExtensionCliTest {

    @TempDir
    Path dataDir;

    @Test
    void readOperationsParseRealCliOutput() {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        var project = Path.of("").toAbsolutePath();
        try (var context = CcManagerApplication.start(new String[] {"--cc-manager.data-dir=" + dataDir})) {
            var plugins = context.getBean(PluginService.class);
            var installed = plugins.installed();
            var available = plugins.available();
            var marketplaces = plugins.marketplaces();
            System.out.println("PLUGINS installed=" + installed + " available=" + available.size() + " marketplaces=" + marketplaces);
            assertThat(installed).allSatisfy(p -> assertThat(p.id()).isNotBlank());
            assertThat(installed).allSatisfy(p -> assertThat(p.scope()).isNotNull());
            assertThat(marketplaces).allSatisfy(m -> assertThat(m.name()).isNotBlank());
            if (!marketplaces.isEmpty()) {
                assertThat(available).isNotEmpty().allSatisfy(p -> assertThat(p.pluginId()).isNotBlank());
            }
            installed.stream().filter(p -> p.scope() == ExtensionScope.USER).findFirst().ifPresent(p -> {
                var details = plugins.details(p.id(), null);
                System.out.println("DETAILS installed=" + details);
                assertThat(details.components()).isNotEmpty();
            });
            installed.stream().filter(p -> p.projectPath() != null && java.nio.file.Files.isDirectory(p.projectPath())).findFirst().ifPresent(p -> {
                var details = plugins.details(p.id(), p.projectPath());
                System.out.println("DETAILS project-scope(" + p.id() + ")=" + details);
                assertThat(details.components()).isNotEmpty();
            });
            if (!available.isEmpty()) {
                var uninstalled = available.stream().filter(a -> installed.stream().noneMatch(i -> i.id().equals(a.pluginId()))).findFirst().orElseThrow();
                var outcome = catchThrowable(() -> plugins.details(uninstalled.pluginId(), null));
                System.out.println("DETAILS catalogue(" + uninstalled.pluginId() + ")=" + outcome);
                assertThat(outcome).isInstanceOf(ExtensionException.class);
            }

            var mcp = context.getBean(McpService.class);
            var servers = mcp.servers(project);
            var health = mcp.health(project);
            System.out.println("MCP servers=" + servers + " health=" + health);
            assertThat(servers).allSatisfy(s -> assertThat(s.name()).isNotBlank());
            assertThat(health).allSatisfy(h -> assertThat(h.status()).isNotNull());

            var skills = context.getBean(SkillService.class);
            var global = skills.list(null);
            var inProject = skills.list(project);
            System.out.println("SKILLS global=" + global.size() + " project=" + inProject.size());
            assertThat(inProject).containsAll(global);
            if (!global.isEmpty()) {
                assertThat(global).allSatisfy(s -> assertThat(s.name()).isNotBlank());
                assertThat(skills.read(global.get(0))).isNotBlank();
            }
        }
    }
}
