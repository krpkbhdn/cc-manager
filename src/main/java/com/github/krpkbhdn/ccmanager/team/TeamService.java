package com.github.krpkbhdn.ccmanager.team;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    /** Role prompts and descriptions together; keeps the claude command line under the Windows limit of 32,767 characters. */
    public static final int MAX_TOTAL_TEXT = 24_000;
    private static final Logger log = LoggerFactory.getLogger(TeamService.class);
    private static final String READ_ONLY_TOOL = "Read";
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
        var totalText = roles.stream().mapToInt(role -> role.prompt().length() + role.description().length()).sum();
        if (totalText > MAX_TOTAL_TEXT) {
            throw new IllegalArgumentException("Role prompts and descriptions total " + totalText + " characters; shorten them to at most "
                    + MAX_TOTAL_TEXT + " so the team can start");
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
        for (int position = 0; position < roles.size(); position++) {
            var role = roles.get(position);
            jdbc.sql("INSERT INTO team_role (team_id, name, description, prompt, model, effort, tools, is_lead, position) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .param(teamId).param(role.name()).param(role.description()).param(role.prompt())
                    .param(role.model()).param(role.effort()).param(role.tools()).param(role.lead() ? 1 : 0).param(position).update();
        }
    }

    private Team team(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        var roles = jdbc.sql(SELECT_ROLES + " WHERE team_id = ? ORDER BY position").param(id).query(TeamService::role).list().stream()
                .flatMap(Optional::stream).toList();
        return new Team(id, rs.getString("name"), roles);
    }

    /** Stale model, effort or tools are repaired field by field; a role that still fails today's rules is skipped, not fatal. */
    private static Optional<TeamRole> role(ResultSet rs, int row) throws SQLException {
        var selection = ModelSelection.stored(rs.getString("model"), rs.getString("effort"));
        try {
            var draft = new RoleDraft(rs.getString("name"), rs.getString("description"), rs.getString("prompt"), selection.model(),
                    selection.effort(), storedTools(rs.getString("tools")), rs.getInt("is_lead") == 1);
            return Optional.of(new TeamRole(rs.getLong("id"), draft, rs.getInt("position")));
        } catch (IllegalArgumentException invalidRole) {
            log.warn("Skipping stored role {}: {}", rs.getLong("id"), invalidRole.getMessage());
            return Optional.empty();
        }
    }

    /** Null (all tools) stays null; entries that are no longer valid are dropped, and a list left with none keeps the most restrictive tool. */
    private static String storedTools(String tools) {
        if (tools == null || tools.isBlank()) {
            return null;
        }
        var valid = Arrays.stream(tools.split(",")).map(String::strip).filter(RoleDraft::isToolEntry).toList();
        return String.join(", ", valid.isEmpty() ? List.of(READ_ONLY_TOOL) : valid);
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }
}
