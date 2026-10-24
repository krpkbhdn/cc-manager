package com.github.krpkbhdn.ccmanager.team;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Teams of role agents, stored in SQLite; "Dev team" is created by the V4 migration only, so a deleted team stays deleted. */
@Service
public class TeamService {

    public static final int MAX_NAME = 60;
    public static final int MAX_ROLES = 12;
    private static final String SELECT_ROLES = "SELECT id, name, description, prompt, model, effort, tools, is_lead, position FROM team_role";

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Autowired
    public TeamService(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public List<Team> list() {
        return jdbc.sql("SELECT id, name FROM team ORDER BY name COLLATE NOCASE, id")
                .query((rs, row) -> team(rs)).list();
    }

    public Optional<Team> find(long id) {
        return jdbc.sql("SELECT id, name FROM team WHERE id = ?").param(id).query((rs, row) -> team(rs)).optional();
    }

    /** @throws IllegalArgumentException with a user-readable message for an invalid team */
    public Team create(String name, List<RoleDraft> roles) {
        var cleanName = validate(name, roles);
        long id = transaction.execute(status -> {
            var keys = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO team (name, created_at) VALUES (?, ?)").param(cleanName).param(Instant.now().toString()).update(keys);
            long teamId = keys.getKey().longValue();
            writeRoles(teamId, roles);
            return teamId;
        });
        fireChanged();
        return find(id).orElseThrow();
    }

    /** Replaces the name and all roles; role ids change, the team id does not. */
    public Team update(long id, String name, List<RoleDraft> roles) {
        var cleanName = validate(name, roles);
        find(id).orElseThrow(() -> new IllegalArgumentException("Team not found: " + id));
        transaction.executeWithoutResult(status -> {
            jdbc.sql("UPDATE team SET name = ? WHERE id = ?").param(cleanName).param(id).update();
            jdbc.sql("DELETE FROM team_role WHERE team_id = ?").param(id).update();
            writeRoles(id, roles);
        });
        fireChanged();
        return find(id).orElseThrow();
    }

    public Team duplicate(long id) {
        var source = find(id).orElseThrow(() -> new IllegalArgumentException("Team not found: " + id));
        var copyName = source.name() + " copy";
        return create(copyName.length() > MAX_NAME ? source.name() : copyName, source.roles().stream().map(TeamRole::draft).toList());
    }

    public void delete(long id) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("UPDATE project SET team_id = NULL WHERE team_id = ?").param(id).update();
            jdbc.sql("DELETE FROM team_role WHERE team_id = ?").param(id).update();
            jdbc.sql("DELETE FROM team WHERE id = ?").param(id).update();
        });
        fireChanged();
    }

    /** Project id to the id of its default team, for projects that have one. */
    public Map<Long, Long> projectDefaults() {
        return jdbc.sql("SELECT id, team_id FROM project WHERE team_id IS NOT NULL")
                .query((rs, row) -> Map.entry(rs.getLong("id"), rs.getLong("team_id"))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** @param teamId null clears the project's default team */
    public void setProjectDefault(long projectId, Long teamId) {
        jdbc.sql("UPDATE project SET team_id = ? WHERE id = ?").param(teamId).param(projectId).update();
        fireChanged();
    }

    /** @return unsubscribe handle; callback fires after any change to teams or a project's default team */
    public Runnable subscribe(Runnable onChange) {
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    /** @return the stripped team name; @throws IllegalArgumentException with a user-readable message */
    public static String validate(String name, List<RoleDraft> roles) {
        var cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME) {
            throw new IllegalArgumentException("Team name must be 1 to " + MAX_NAME + " characters");
        }
        if (roles.isEmpty() || roles.size() > MAX_ROLES) {
            throw new IllegalArgumentException("A team needs 1 to " + MAX_ROLES + " roles");
        }
        if (roles.stream().filter(RoleDraft::lead).count() != 1) {
            throw new IllegalArgumentException("Mark exactly one role as the lead");
        }
        var names = new HashSet<String>();
        roles.forEach(role -> {
            if (!names.add(role.name())) {
                throw new IllegalArgumentException("Role names must be unique: " + role.name());
            }
        });
        return cleanName;
    }

    private void writeRoles(long teamId, List<RoleDraft> roles) {
        Long leadId = null;
        for (int position = 0; position < roles.size(); position++) {
            var role = roles.get(position);
            var keys = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO team_role (team_id, name, description, prompt, model, effort, tools, is_lead, position) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .param(teamId).param(role.name()).param(role.description()).param(role.prompt())
                    .param(role.model()).param(role.effort()).param(role.tools()).param(role.lead() ? 1 : 0).param(position).update(keys);
            if (role.lead()) {
                leadId = keys.getKey().longValue();
            }
        }
        jdbc.sql("UPDATE team SET lead_role_id = ? WHERE id = ?").param(leadId).param(teamId).update();
    }

    private Team team(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        var roles = jdbc.sql(SELECT_ROLES + " WHERE team_id = ? ORDER BY position").param(id).query(TeamService::role).list();
        return new Team(id, rs.getString("name"), roles);
    }

    private static TeamRole role(ResultSet rs, int row) throws SQLException {
        var draft = new RoleDraft(rs.getString("name"), rs.getString("description"), rs.getString("prompt"), rs.getString("model"),
                rs.getString("effort"), rs.getString("tools"), rs.getInt("is_lead") == 1);
        return new TeamRole(rs.getLong("id"), draft, rs.getInt("position"));
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }
}
