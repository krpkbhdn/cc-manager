package com.github.krpkbhdn.ccmanager.author;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Remembers, per component and project, whether the author's setup was installed or declined and in which version. */
@Service
public class AuthorDecisionService {

    private final JdbcClient jdbc;

    public AuthorDecisionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthorDecision> find(AuthorComponent component, long projectId) {
        return jdbc.sql("SELECT status, version FROM author_decision WHERE component = ? AND project_id = ?")
                .param(component.name()).param(projectId)
                .query((rs, row) -> new AuthorDecision(component, projectId, AuthorChoice.valueOf(rs.getString("status")), rs.getInt("version")))
                .optional();
    }

    public void record(AuthorComponent component, long projectId, AuthorChoice choice, int version) {
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (component, project_id) DO UPDATE SET status = excluded.status, version = excluded.version, "
                        + "decided_at = CURRENT_TIMESTAMP")
                .param(component.name()).param(projectId).param(choice.name()).param(version).update();
    }
}
