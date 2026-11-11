package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.author.install.ExtensionInstaller;
import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;
import com.github.krpkbhdn.ccmanager.author.install.SettingsInstaller;
import com.github.krpkbhdn.ccmanager.author.install.TeamsInstaller;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** The author's bundled user-level catalog as the UI sees it: what is offered, installing the chosen items, remembering declines. */
@Service
public class AuthorCatalogService {

    private final AuthorProfileLoader loader;
    private final AuthorDecisionService decisions;
    private final ExtensionInstaller extensions;
    private final TeamsInstaller teams;
    private final SettingsInstaller settings;
    private final TeamService teamService;

    public AuthorCatalogService(AuthorProfileLoader loader, AuthorDecisionService decisions, ExtensionInstaller extensions,
            TeamsInstaller teams, SettingsInstaller settings, TeamService teamService) {
        this.loader = loader;
        this.decisions = decisions;
        this.extensions = extensions;
        this.teams = teams;
        this.settings = settings;
        this.teamService = teamService;
    }

    public AuthorCatalog catalog() {
        return loader.catalog();
    }

    /** Every catalog item with its status. */
    public List<AuthorEntry> entries() {
        var stored = decisions.findAll();
        var existingTeams = existingTeamNames();
        var entries = new ArrayList<AuthorEntry>();
        for (var item : loader.catalog().items()) {
            var decision = Optional.ofNullable(stored.get(item.id()));
            var installed = decision.filter(found -> found.choice() == AuthorChoice.INSTALLED).map(AuthorDecision::version).orElse(0);
            var status = AuthorStatus.of(decision, item.version());
            var changes = status == AuthorStatus.UPDATE_AVAILABLE ? item.changesSince(installed) : List.<String>of();
            var replaces = item.kind() == AuthorKind.TEAM && status != AuthorStatus.INSTALLED && existingTeams.contains(loader.teamTemplate(item.id()).name());
            entries.add(new AuthorEntry(item, installed, status, changes, replaces));
        }
        return List.copyOf(entries);
    }

    /** Blocking. Installs the chosen items and what they require; an item that installs without failure is recorded as installed at once. */
    public AuthorInstallReport install(Collection<String> selectedIds) {
        var items = loader.catalog().requiresClosure(selectedIds).stream().sorted(Comparator.comparing(AuthorItem::kind)).toList();
        var outcomes = new ArrayList<ItemOutcome>();
        for (var item : items) {
            var outcome = installItem(item);
            outcomes.add(new ItemOutcome(item.id(), outcome));
            if (!outcome.hasFailures()) {
                record(item, AuthorChoice.INSTALLED);
            }
        }
        return new AuthorInstallReport(outcomes);
    }

    /** Records the items as declined, so they are not offered again unprompted. */
    public void decline(Collection<String> itemIds) {
        itemIds.forEach(id -> record(item(id), AuthorChoice.DECLINED));
    }

    private void record(AuthorItem item, AuthorChoice choice) {
        decisions.record(item.id(), choice, item.version());
    }

    private AuthorItem item(String id) {
        return loader.catalog().byId(id).orElseThrow(() -> new IllegalArgumentException("unknown item " + id));
    }

    private InstallOutcome installItem(AuthorItem item) {
        return switch (item.kind()) {
            case TEAM -> teams.install(List.of(item));
            case SETTINGS -> settings.install(item);
            default -> extensions.install(extensionSet(item), ExtensionScope.USER, null);
        };
    }

    private ExtensionSet extensionSet(AuthorItem item) {
        return switch (item.kind()) {
            case MARKETPLACE -> new ExtensionSet(List.of(new ProfileMarketplace(item.name(), item.source())), null, null, null);
            case PLUGIN -> new ExtensionSet(null, List.of(item.name()), null, null);
            case SKILL -> new ExtensionSet(null, null, List.of(loader.skill(item)), null);
            case MCP_SERVER -> new ExtensionSet(null, null, null, List.of(new ProfileMcpServer(item.name(), item.config())));
            default -> throw new IllegalArgumentException(item.id() + " is not an extension");
        };
    }

    private Set<String> existingTeamNames() {
        return teamService.list().stream().map(Team::name).collect(Collectors.toSet());
    }
}
