package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import java.util.List;

public record TeamTemplate(String name, List<RoleDraft> roles) {

    public TeamTemplate {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
