package com.github.krpkbhdn.ccmanager.team;

import java.util.List;
import java.util.Optional;

public record Team(long id, String name, List<TeamRole> roles) {

    public Team {
        roles = List.copyOf(roles);
    }

    public Optional<TeamRole> lead() {
        return roles.stream().filter(TeamRole::lead).findFirst();
    }

    /** The roles the lead can delegate to. */
    public List<TeamRole> subagents() {
        return roles.stream().filter(role -> !role.lead()).toList();
    }
}
