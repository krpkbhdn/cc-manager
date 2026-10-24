package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.hook.HookSettingsWriter;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
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
    private final ClaudeLauncher launcher = new ClaudeLauncher(projects, installation, hooks, sessions);

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

    @Test
    void unknownProjectIsReported() {
        when(projects.find(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> launcher.launch(1, LaunchMode.NEW)).hasMessageContaining("project");
    }

    @Test
    void restartRejectsMissingOrUnsafeConversationIds() {
        assertThatThrownBy(() -> launcher.restart(1, null, ModelSelection.DEFAULT)).hasMessageContaining("no conversation");
        assertThatThrownBy(() -> launcher.restart(1, "a b;c", ModelSelection.DEFAULT)).isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).register(anyLong());
    }

    @Test
    void spawnFailureEndsSessionAndDeletesSettings() throws Exception {
        Path notExecutable = Files.createFile(directory.resolve("claude-fake"));
        when(installation.current()).thenReturn(new ClaudeInstallation(true, notExecutable, "1", null, null));
        when(sessions.register(1)).thenReturn("ccm-1");
        when(hooks.write("ccm-1")).thenReturn(directory.resolve("s.json"));

        assertThatThrownBy(() -> launcher.launch(1, LaunchMode.NEW))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Could not start Claude");

        verify(sessions).markEnded("ccm-1", -1);
        verify(hooks).delete("ccm-1");
    }
}
