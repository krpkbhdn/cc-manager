package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.author.install.ExtensionInstaller;
import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;
import com.github.krpkbhdn.ccmanager.author.install.TeamsInstaller;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorProfileServiceTest {

    static final Path PROJECT = Path.of("work").toAbsolutePath();
    static final InstallOutcome FAILED = new InstallOutcome(List.of(), List.of(), List.of(), List.of("x"), List.of());

    final AuthorProfileLoader loader = new AuthorProfileLoader();
    final AuthorDecisionService decisions = mock(AuthorDecisionService.class);
    final ExtensionInstaller extensions = mock(ExtensionInstaller.class);
    final TeamsInstaller teams = mock(TeamsInstaller.class);
    final AuthorSessionPrompt prompt = mock(AuthorSessionPrompt.class);
    final AuthorProfileService service = new AuthorProfileService(loader, decisions, extensions, teams, prompt);
    final SessionHandoff handoff = new SessionHandoff("Read it.", PROJECT);

    AuthorEntry entry(AuthorComponent component, long projectId) {
        return new AuthorEntry(component, projectId, "t", "s", 1, 0, AuthorStatus.NOT_INSTALLED);
    }

    @Test
    void entriesCoverUserComponentsAndProjectSetupOnlyForAProject() {
        when(decisions.find(any(), anyLong())).thenReturn(Optional.empty());

        assertThat(service.entries(null)).extracting(AuthorEntry::component)
                .containsExactly(AuthorComponent.USER_SETUP, AuthorComponent.TEAMS);
        var withProject = service.entries(5L);
        assertThat(withProject).extracting(AuthorEntry::component).contains(AuthorComponent.PROJECT_SETUP);
        assertThat(withProject.getLast().projectId()).isEqualTo(5);
        assertThat(withProject.getFirst().status()).isEqualTo(AuthorStatus.NOT_INSTALLED);
    }

    @Test
    void userSetupIsHandedToClaudeButOnlyRecordedWhenMarkedInstalled() {
        when(extensions.install(any(), any(), any())).thenReturn(InstallOutcome.NONE);
        when(prompt.prepare(any(), any(), any())).thenReturn(handoff);
        var user = entry(AuthorComponent.USER_SETUP, 0);

        var report = service.install(List.of(user), PROJECT);

        verify(extensions).install(loader.profile().userSetup().extensions(), ExtensionScope.USER, null);
        verify(prompt).prepare(Set.of(AuthorComponent.USER_SETUP), PROJECT, report.components());
        assertThat(report.handoff()).contains(handoff);
        assertThat(report.awaitingSession()).containsExactly(user);
        verify(decisions, never()).record(any(), anyLong(), any(), anyInt());

        service.markInstalled(report.awaitingSession());

        verify(decisions).record(AuthorComponent.USER_SETUP, 0, AuthorChoice.INSTALLED, 1);
    }

    @Test
    void nothingIsRecordedWhenPreparingTheSessionFails() {
        when(teams.install(anyBoolean())).thenReturn(InstallOutcome.NONE);
        when(extensions.install(any(), any(), any())).thenReturn(InstallOutcome.NONE);
        when(prompt.prepare(any(), any(), any())).thenThrow(new UncheckedIOException(new IOException("disk")));

        assertThatThrownBy(() -> service.install(List.of(entry(AuthorComponent.TEAMS, 0), entry(AuthorComponent.USER_SETUP, 0)), PROJECT))
                .isInstanceOf(UncheckedIOException.class);

        verify(decisions, never()).record(any(), anyLong(), any(), anyInt());
    }
    @Test
    void failedComponentIsNotRecordedAndGetsNoSession() {
        when(extensions.install(any(), any(), any())).thenReturn(FAILED);

        var report = service.install(List.of(entry(AuthorComponent.USER_SETUP, 0)), PROJECT);

        verify(decisions, never()).record(any(), anyLong(), any(), anyInt());
        assertThat(report.handoff()).isEmpty();
        assertThat(report.components().getFirst().failed()).isTrue();
    }

    @Test
    void teamsOnlyHasNoHandoffAndProjectSetupInstallsInTheProject() {
        when(teams.install(anyBoolean())).thenReturn(InstallOutcome.NONE);
        when(extensions.install(any(), any(), any())).thenReturn(InstallOutcome.NONE);
        when(prompt.prepare(any(), any(), any())).thenReturn(handoff);

        assertThat(service.install(List.of(entry(AuthorComponent.TEAMS, 0)), null).handoff()).isEmpty();
        verify(decisions).record(AuthorComponent.TEAMS, 0, AuthorChoice.INSTALLED, 1);
        var report = service.install(List.of(entry(AuthorComponent.PROJECT_SETUP, 3)), PROJECT);
        service.markInstalled(report.awaitingSession());

        verify(extensions).install(loader.profile().projectSetup().extensions(), ExtensionScope.PROJECT, PROJECT);
        verify(decisions).record(AuthorComponent.PROJECT_SETUP, 3, AuthorChoice.INSTALLED, 1);
    }

    @Test
    void projectSetupWithoutAProjectPathIsRejected() {
        assertThatThrownBy(() -> service.install(List.of(entry(AuthorComponent.PROJECT_SETUP, 3)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void declineRecordsTheBundledVersion() {
        service.decline(List.of(entry(AuthorComponent.TEAMS, 0)));

        verify(decisions).record(AuthorComponent.TEAMS, 0, AuthorChoice.DECLINED, 1);
    }
}
