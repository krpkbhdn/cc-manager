package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.author.install.ExtensionInstaller;
import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;
import com.github.krpkbhdn.ccmanager.author.install.SettingsInstaller;
import com.github.krpkbhdn.ccmanager.author.install.TeamsInstaller;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;

class AuthorCatalogServiceTest {

    final AuthorProfileLoader loader = new AuthorProfileLoader();
    final AuthorDecisionService decisions = mock(AuthorDecisionService.class);
    final ExtensionInstaller extensions = mock(ExtensionInstaller.class);
    final TeamsInstaller teams = mock(TeamsInstaller.class);
    final SettingsInstaller settings = mock(SettingsInstaller.class);
    final TeamService teamService = mock(TeamService.class);
    final AuthorCatalogService service = new AuthorCatalogService(loader, decisions, extensions, teams, settings, teamService);

    AuthorEntry entry(List<AuthorEntry> entries, String id) {
        return entries.stream().filter(entry -> entry.item().id().equals(id)).findFirst().orElseThrow();
    }

    InstallOutcome installed(String what) {
        return new InstallOutcome(List.of(what), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void entriesStartNotInstalled() {
        assertThat(entry(service.entries(), "plugin.ponytail").status()).isEqualTo(AuthorStatus.NOT_INSTALLED);
    }

    @Test
    void entriesTakeStatusFromTheStoredDecisions() {
        when(decisions.findAll()).thenReturn(Map.of("plugin.ponytail", new AuthorDecision("plugin.ponytail", AuthorChoice.INSTALLED, 1),
                "team.dev-team", new AuthorDecision("team.dev-team", AuthorChoice.INSTALLED, 1),
                "skill.jscpd", new AuthorDecision("skill.jscpd", AuthorChoice.DECLINED, 1)));

        var entries = service.entries();

        assertThat(entry(entries, "plugin.ponytail").status()).isEqualTo(AuthorStatus.INSTALLED);
        assertThat(entry(entries, "skill.jscpd").status()).isEqualTo(AuthorStatus.DECLINED);
        var team = entry(entries, "team.dev-team");
        assertThat(team.status()).isEqualTo(AuthorStatus.UPDATE_AVAILABLE);
        assertThat(team.installedVersion()).isEqualTo(1);
        assertThat(team.changes()).containsExactly("Added researcher and debater roles", "Teams snapshot from the author's current setup");
    }

    @Test
    void teamEntryReplacesExistingWhenATeamWithTheNameExists() {
        when(teamService.list()).thenReturn(List.of(new Team(1, "Dev team", List.of())));

        assertThat(entry(service.entries(), "team.dev-team").replacesExisting()).isTrue();
        assertThat(entry(service.entries(), "plugin.ponytail").replacesExisting()).isFalse();
    }

    @Test
    void installAddsRequirementsInstallsMarketplaceBeforePluginAndRecordsEach() {
        when(extensions.install(any(), any(), any())).thenReturn(installed("x"));

        var report = service.install(List.of("plugin.context7"));

        var order = inOrder(extensions);
        order.verify(extensions).install(argThat(set -> !set.marketplaces().isEmpty()), eq(ExtensionScope.USER), eq(null));
        order.verify(extensions).install(argThat(set -> set.plugins().equals(List.of("context7@claude-plugins-official"))),
                eq(ExtensionScope.USER), eq(null));
        verify(decisions).record("marketplace.claude-plugins-official", AuthorChoice.INSTALLED, 1);
        verify(decisions).record("plugin.context7", AuthorChoice.INSTALLED, 1);
        assertThat(report.outcomes()).extracting(ItemOutcome::itemId).containsExactly("marketplace.claude-plugins-official", "plugin.context7");
    }

    @Test
    void failedItemIsNotRecorded() {
        when(extensions.install(any(), any(), any())).thenReturn(new InstallOutcome(List.of(), List.of(), List.of(), List.of("plugin x: boom"), List.of()));

        service.install(List.of("plugin.ponytail"));

        verify(decisions, never()).record(eq("plugin.ponytail"), any(), anyInt());
    }

    @Test
    void teamIsInstalledByTheTeamsInstallerAndRecordedAtItsVersion() {
        when(teams.install(anyList())).thenReturn(installed("team Dev team"));

        service.install(List.of("team.dev-team"));

        verify(decisions).record("team.dev-team", AuthorChoice.INSTALLED, 3);
    }

    @Test
    void settingsItemIsMergedByTheSettingsInstallerAndRecorded() {
        var item = loader.catalog().byId("user.settings.attribution").orElseThrow();
        when(settings.install(item)).thenReturn(installed("settings"));

        service.install(List.of("user.settings.attribution"));

        verify(settings).install(item);
        verify(decisions).record("user.settings.attribution", AuthorChoice.INSTALLED, item.version());
    }

    @Test
    void declineRecordsTheItemsAtTheirVersion() {
        service.decline(List.of("team.dev-team", "plugin.ponytail"));

        verify(decisions).record("team.dev-team", AuthorChoice.DECLINED, 3);
        verify(decisions).record("plugin.ponytail", AuthorChoice.DECLINED, 1);
    }

    static <T> T argThat(ArgumentMatcher<T> matcher) {
        return ArgumentMatchers.argThat(matcher);
    }
}
