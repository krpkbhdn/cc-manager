package com.github.krpkbhdn.ccmanager.author.install;

import com.github.krpkbhdn.ccmanager.author.AuthorProfileLoader;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Creates the profile's agent teams that do not exist yet and replaces the roles of the ones that do. */
@Service
public class TeamsInstaller {

    private final TeamService teams;
    private final AuthorProfileLoader loader;

    public TeamsInstaller(TeamService teams, AuthorProfileLoader loader) {
        this.teams = teams;
        this.loader = loader;
    }

    public InstallOutcome install() {
        var installed = new ArrayList<String>();
        var failed = new ArrayList<String>();
        var existing = teams.list();
        for (var template : loader.teamTemplates()) {
            var label = "team " + template.name();
            try {
                var current = existing.stream().filter(team -> team.name().equals(template.name())).findFirst();
                if (current.isEmpty()) {
                    teams.create(template.name(), template.roles());
                } else {
                    teams.update(current.get().id(), template.name(), template.roles());
                }
                installed.add(label);
            } catch (IllegalArgumentException e) {
                failed.add(label + ": " + e.getMessage());
            }
        }
        return new InstallOutcome(installed, List.of(), List.of(), failed, List.of());
    }
}
