package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.hook.HookSettingsWriter;
import com.github.krpkbhdn.ccmanager.claude.hook.SessionFiles;
import com.github.krpkbhdn.ccmanager.claude.plugin.ClaudePluginWriter;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.extension.mcp.ProjectMcpServers;
import com.github.krpkbhdn.ccmanager.project.GroupWorkspace;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService;
import java.util.List;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeLauncherTest {

    private final ProjectService projects = mock(ProjectService.class);
    private final ClaudeInstallationService installation = mock(ClaudeInstallationService.class);
    private final HookSettingsWriter hooks = mock(HookSettingsWriter.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final ProjectGroupService groups = mock(ProjectGroupService.class);
    private final ClaudeLauncher launcher = new ClaudeLauncher(projects, installation, hooks, mock(ClaudePluginWriter.class), sessions,
            groups, mock(GroupWorkspace.class), mock(ProjectMcpServers.class));

    @TempDir
    Path directory;

    @BeforeEach
    void projectExists() {
        when(projects.find(anyLong())).thenReturn(Optional.of(new Project(1, "p", directory, Instant.now())));
    }

    @Test
    void missingClaudeGivesReadableMessageAndRegistersNothing() {
        when(installation.current()).thenReturn(new ClaudeInstallation(false, null, null, null, "Claude is missing"));

        assertThatThrownBy(() -> launcher.launch(1, LaunchMode.NEW))
                .isInstanceOf(IllegalStateException.class).hasMessage("Claude is missing");
        verify(sessions, never()).register(anyLong());
    }

    private Project group() {
        return new Project(2, "g", directory, Instant.now(), ModelSelection.DEFAULT, true, null);
    }

    @Test
    void groupWithoutMembersIsRefused() {
        when(projects.find(2)).thenReturn(Optional.of(group()));
        when(groups.members(2)).thenReturn(List.of());
        when(installation.current()).thenReturn(new ClaudeInstallation(true, directory, "1", null, null));

        assertThatThrownBy(() -> launcher.launch(2, LaunchMode.NEW)).hasMessageContaining("no projects");
        verify(sessions, never()).register(anyLong());
    }

    @Test
    void groupWithMissingMemberFolderNamesIt() {
        var gone = new Project(3, "gone", directory.resolve("nope"), Instant.now());
        when(projects.find(2)).thenReturn(Optional.of(group()));
        when(groups.members(2)).thenReturn(List.of(gone));

        assertThatThrownBy(() -> launcher.launch(2, LaunchMode.NEW)).hasMessageContaining("folder is missing").hasMessageContaining("gone");
        verify(sessions, never()).register(anyLong());
    }

    @Test
    void attachIsRefusedForAGroup() {
        when(projects.find(2)).thenReturn(Optional.of(group()));

        assertThatThrownBy(() -> launcher.attach(2, "ab12")).hasMessageContaining("group");
        verify(sessions, never()).register(anyLong());
    }

    @Test
    void unknownProjectIsReported() {
        when(projects.find(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> launcher.launch(1, LaunchMode.NEW)).hasMessageContaining("project");
    }

    @Test
    void spawnFailureEndsSessionAndDeletesSettings() throws Exception {
        Path notExecutable = Files.createFile(directory.resolve("claude-fake"));
        when(installation.current()).thenReturn(new ClaudeInstallation(true, notExecutable, "1", null, null));
        when(sessions.register(1)).thenReturn("ccm-1");
        when(hooks.write("ccm-1")).thenReturn(new SessionFiles(directory.resolve("s.json"), directory.resolve("m.json")));

        assertThatThrownBy(() -> launcher.launch(1, LaunchMode.NEW))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Could not start Claude");

        verify(sessions).markEnded("ccm-1", -1);
        verify(hooks).delete("ccm-1");
    }

    @Test
    void setupSpawnFailureIsReportedLikeAnyOtherLaunch() throws Exception {
        Path notExecutable = Files.createFile(directory.resolve("claude-fake"));
        when(installation.current()).thenReturn(new ClaudeInstallation(true, notExecutable, "1", null, null));
        when(sessions.register(1)).thenReturn("ccm-1");
        when(hooks.write("ccm-1")).thenReturn(new SessionFiles(directory.resolve("s.json"), directory.resolve("m.json")));

        assertThatThrownBy(() -> launcher.launchSetup(1, "Read it.", directory))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Could not start Claude");
        verify(sessions).markEnded("ccm-1", -1);
    }
}
