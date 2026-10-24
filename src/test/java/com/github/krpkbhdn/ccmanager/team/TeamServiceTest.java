package com.github.krpkbhdn.ccmanager.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class TeamServiceTest {

    @TempDir
    Path temp;

    TeamService service;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new TeamService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    static RoleDraft role(String name, boolean lead) {
        return new RoleDraft(name, "when to use " + name, "You are " + name, "sonnet", "medium", null, lead);
    }

    @Test
    void migrationSeedsTheDevTeamWithLeadFirst() {
        var team = service.list().getFirst();

        assertThat(team.name()).isEqualTo("Dev team");
        assertThat(team.roles()).extracting(TeamRole::name).containsExactly("architect", "developer", "tester");
        assertThat(team.lead().orElseThrow().selection().model()).isEqualTo("opus");
        assertThat(team.subagents()).extracting(role -> role.selection().effort()).containsExactly("medium", "low");
    }

    @Test
    void deletedSeedTeamStaysDeleted() {
        service.delete(service.list().getFirst().id());

        assertThat(service.list()).isEmpty();
    }

    @Test
    void createStoresRolesInOrderAndSetsLeadRole() {
        var team = service.create("Squad", List.of(role("dev", false), role("boss", true)));

        assertThat(team.roles()).extracting(TeamRole::name).containsExactly("dev", "boss");
        assertThat(jdbc.sql("SELECT lead_role_id FROM team WHERE id = ?").param(team.id()).query(Long.class).single())
                .isEqualTo(team.lead().orElseThrow().id());
    }

    @Test
    void updateReplacesRolesAndKeepsTheTeamId() {
        var team = service.create("Squad", List.of(role("boss", true)));

        var updated = service.update(team.id(), "Renamed", List.of(role("lead", true), role("qa", false)));

        assertThat(updated.id()).isEqualTo(team.id());
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.roles()).extracting(TeamRole::name).containsExactly("lead", "qa");
    }

    @Test
    void duplicateCopiesRolesUnderANewName() {
        var original = service.list().getFirst();

        var copy = service.duplicate(original.id());

        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("Dev team copy");
        assertThat(copy.roles()).extracting(TeamRole::name).containsExactly("architect", "developer", "tester");
    }

    @Test
    void validationRejectsBadTeams() {
        assertThatThrownBy(() -> service.create(" ", List.of(role("a", true)))).hasMessageContaining("Team name");
        assertThatThrownBy(() -> service.create("T", List.of())).hasMessageContaining("roles");
        assertThatThrownBy(() -> service.create("T", List.of(role("a", false)))).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> service.create("T", List.of(role("a", true), role("b", true)))).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> service.create("T", List.of(role("a", true), role("a", false)))).hasMessageContaining("unique");
    }

    @Test
    void roleValidationReusesModelRulesAndSlugNames() {
        assertThatThrownBy(() -> new RoleDraft("Bad Name", "d", "p", null, null, null, false)).hasMessageContaining("Role name");
        assertThatThrownBy(() -> new RoleDraft("ok", "d", "p", "-evil", null, null, false)).hasMessageContaining("model");
        assertThatThrownBy(() -> new RoleDraft("ok", "d", "p", "two words", null, null, false)).hasMessageContaining("model");
        assertThatThrownBy(() -> new RoleDraft("ok", "d", "p", null, "extreme", null, false)).hasMessageContaining("effort");
        assertThatThrownBy(() -> new RoleDraft("ok", "", "p", null, null, null, false)).hasMessageContaining("when");
        assertThatThrownBy(() -> new RoleDraft("ok", "d", "", null, null, null, false)).hasMessageContaining("prompt");
        assertThat(new RoleDraft("ok", "d", "p", " ", "", " ", false).tools()).isNull();
    }

    @Test
    void projectDefaultFollowsAssignmentAndClearsWhenTheTeamIsDeleted() {
        jdbc.sql("INSERT INTO project (name, path) VALUES ('p', 'x')").update();
        long projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        var team = service.list().getFirst();
        var changes = new AtomicInteger();
        service.subscribe(changes::incrementAndGet);

        assertThat(service.projectDefaults()).isEmpty();
        service.setProjectDefault(projectId, team.id());
        assertThat(service.projectDefaults()).containsEntry(projectId, team.id());
        service.delete(team.id());

        assertThat(service.projectDefaults()).isEmpty();
        assertThat(changes).hasValue(2);
    }
}
