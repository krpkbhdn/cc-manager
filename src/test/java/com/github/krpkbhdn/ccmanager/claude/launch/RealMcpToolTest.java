package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.claude.hook.HookSettingsWriter;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.plugin.ClaudePluginWriter;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.run.RunConfiguration;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real claude -p (haiku, one prompt) with the files a real launch writes: the cc-manager MCP tool must create a run
 * configuration and the plugin skill must be visible. Set the logger of claude.hook and claude.mcp to DEBUG to see what Claude sends.
 */
class RealMcpToolTest {

    private static final int TIMEOUT_SECONDS = 180;

    @TempDir
    Path dataDir;

    @TempDir
    Path projectDir;

    @Test
    void claudeCallsTheRunConfigurationToolAndSeesThePluginSkill() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_CLAUDE")));
        try (var context = CcManagerApplication.start(new String[] {
                "--cc-manager.data-dir=" + dataDir, "--logging.level.com.github.krpkbhdn.ccmanager.claude=DEBUG"})) {
            var project = context.getBean(ProjectService.class).add(projectDir);
            var ccmSessionId = context.getBean(SessionRegistry.class).register(project.id());
            var files = context.getBean(HookSettingsWriter.class).write(ccmSessionId);
            var executable = context.getBean(ClaudeInstallationService.class).current().executable();
            var pluginDir = context.getBean(ClaudePluginWriter.class).directory();

            var command = new ArrayList<>(ClaudeCommand.launchArguments(executable, files, pluginDir, LaunchMode.NEW, ModelSelection.DEFAULT));
            command.addAll(List.of("-p", "--model", "haiku", "--", "Call the cc-manager MCP tool set_run_configuration with name 'probe', "
                    + "command 'echo hi', directory '" + projectDir.toAbsolutePath() + "'. "
                    + "Then list your available skills whose name starts with cc-manager and print them."));
            var environment = ClaudeCommand.environment(System.getenv(), ccmSessionId);
            var builder = new ProcessBuilder(command).directory(projectDir.toFile()).redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().putAll(environment);
            var process = builder.start();
            process.getOutputStream().close();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            System.out.println("CLAUDE_OUTPUT=" + output);

            var names = context.getBean(RunConfigurationService.class).list(project.id()).stream().map(RunConfiguration::name).toList();
            assertThat(names).contains("probe");
            assertThat(output).contains("run-configurations");
        }
    }
}
