package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.author.install.ExtensionInstaller;
import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;
import com.github.krpkbhdn.ccmanager.author.install.TeamsInstaller;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** The author's bundled setup as the UI sees it: what is offered, installing the chosen parts, remembering declines. */
@Service
public class AuthorProfileService {

    private static final long USER_PROJECT_ID = 0;

    private final AuthorProfileLoader loader;
    private final AuthorDecisionService decisions;
    private final ExtensionInstaller extensions;
    private final TeamsInstaller teams;
    private final TeamService teamService;
    private final AuthorSessionPrompt prompt;

    public AuthorProfileService(AuthorProfileLoader loader, AuthorDecisionService decisions, ExtensionInstaller extensions,
            TeamsInstaller teams, TeamService teamService, AuthorSessionPrompt prompt) {
        this.loader = loader;
        this.decisions = decisions;
        this.extensions = extensions;
        this.teams = teams;
        this.teamService = teamService;
        this.prompt = prompt;
    }

    /** User components and teams, plus the project setup when a project is given. */
    public List<AuthorEntry> entries(Long projectId) {
        var entries = new ArrayList<AuthorEntry>();
        entries.add(entry(AuthorComponent.USER_SETUP, USER_PROJECT_ID));
        entries.add(entry(AuthorComponent.TEAMS, USER_PROJECT_ID));
        if (projectId != null) {
            entries.add(entry(AuthorComponent.PROJECT_SETUP, projectId));
        }
        return List.copyOf(entries);
    }

    /**
     * Blocking. Teams without failures are recorded as installed at once; the user and project setup are recorded only by
     * {@link #markInstalled} once the Claude session that finishes them started.
     *
     * @param projectPath the project Claude works in; without one the Claude-finished components stay in
     *                    {@code awaitingSession} with no handoff, to be prepared later by {@link #handoff}
     */
    public AuthorInstallReport install(List<AuthorEntry> chosen, Path projectPath) {
        if (projectPath == null && chosen.stream().anyMatch(entry -> entry.component() == AuthorComponent.PROJECT_SETUP)) {
            throw new IllegalArgumentException("The project setup needs a project path");
        }
        var outcomes = new ArrayList<ComponentOutcome>();
        var succeeded = new ArrayList<AuthorEntry>();
        for (var entry : chosen) {
            var outcome = install(entry, projectPath);
            outcomes.add(new ComponentOutcome(entry.component(), outcome));
            if (!outcome.hasFailures()) {
                succeeded.add(entry);
            }
        }
        var awaiting = succeeded.stream().filter(entry -> entry.component() != AuthorComponent.TEAMS).toList();
        var handoff = awaiting.isEmpty() || projectPath == null ? Optional.<SessionHandoff>empty()
                : Optional.of(handoff(awaiting, projectPath, outcomes));
        succeeded.stream().filter(entry -> entry.component() == AuthorComponent.TEAMS).forEach(this::markInstalled);
        return new AuthorInstallReport(outcomes, handoff, awaiting);
    }

    /** Prepares the session that finishes the report's {@code awaitingSession} entries in the project. */
    public SessionHandoff handoff(AuthorInstallReport report, Path projectPath) {
        return handoff(report.awaitingSession(), projectPath, report.components());
    }

    private SessionHandoff handoff(List<AuthorEntry> awaiting, Path projectPath, List<ComponentOutcome> outcomes) {
        var claudeComponents = EnumSet.noneOf(AuthorComponent.class);
        awaiting.forEach(entry -> claudeComponents.add(entry.component()));
        return prompt.prepare(claudeComponents, projectPath, outcomes);
    }

    /** Records the entries as installed; called once the session that finishes them started. */
    public void markInstalled(List<AuthorEntry> entries) {
        entries.forEach(this::markInstalled);
    }

    private void markInstalled(AuthorEntry entry) {
        decisions.record(entry.component(), entry.projectId(), AuthorChoice.INSTALLED, entry.bundledVersion());
    }

    public void decline(List<AuthorEntry> entries) {
        entries.forEach(entry -> decisions.record(entry.component(), entry.projectId(), AuthorChoice.DECLINED, entry.bundledVersion()));
    }

    private InstallOutcome install(AuthorEntry entry, Path projectPath) {
        var profile = loader.profile();
        return switch (entry.component()) {
            case USER_SETUP -> extensions.install(profile.userSetup().extensions(), ExtensionScope.USER, null);
            case PROJECT_SETUP -> extensions.install(profile.projectSetup().extensions(), ExtensionScope.PROJECT, projectPath);
            case TEAMS -> teams.install();
        };
    }

    private AuthorEntry entry(AuthorComponent component, long projectId) {
        var profile = loader.profile();
        var decision = decisions.find(component, projectId);
        var bundled = profile.version(component);
        var installed = decision.filter(found -> found.choice() == AuthorChoice.INSTALLED).map(AuthorDecision::version).orElse(0);
        var status = AuthorStatus.of(decision, bundled);
        var changes = status == AuthorStatus.UPDATE_AVAILABLE ? profile.changesSince(component, installed) : List.<String>of();
        return new AuthorEntry(component, projectId, profile.title(component), profile.summary(component), bundled, installed, status, changes,
                component == AuthorComponent.TEAMS && replacesExistingTeam());
    }

    private boolean replacesExistingTeam() {
        var existing = teamService.list().stream().map(Team::name).collect(Collectors.toSet());
        return loader.teamTemplates().stream().anyMatch(template -> existing.contains(template.name()));
    }
}
