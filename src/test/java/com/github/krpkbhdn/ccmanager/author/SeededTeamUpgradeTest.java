package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.author.install.ExtensionInstaller;
import com.github.krpkbhdn.ccmanager.author.install.SettingsInstaller;
import com.github.krpkbhdn.ccmanager.author.install.TeamsInstaller;
import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import com.github.krpkbhdn.ccmanager.team.TeamRole;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class SeededTeamUpgradeTest {

    @TempDir
    Path temp;

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;
    TeamService teams;
    AuthorDecisionService decisions;
    AuthorProfileLoader loader = new AuthorProfileLoader();
    SeededTeamUpgrade upgrade;
    AuthorCatalogService catalog;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db"));
        jdbc = JdbcClient.create(dataSource);
        teams = new TeamService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        decisions = new AuthorDecisionService(jdbc);
        var installer = new TeamsInstaller(teams, loader);
        upgrade = new SeededTeamUpgrade(teams, installer, loader, decisions);
        catalog = new AuthorCatalogService(loader, decisions, Mockito.mock(ExtensionInstaller.class), installer,
                Mockito.mock(SettingsInstaller.class), teams);
    }

    void migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    List<RoleDraft> drafts() {
        return teams.list().getFirst().roles().stream().map(TeamRole::draft).toList();
    }

    AuthorEntry teamEntry() {
        return catalog.entries().stream().filter(entry -> entry.item().id().equals("team.dev-team")).findFirst().orElseThrow();
    }

    @Test
    void legacyRolesEqualTheV4Seed() {
        Flyway.configure().dataSource(dataSource).target("4").load().migrate();

        assertThat(drafts()).isEqualTo(SeededTeamUpgrade.LEGACY_ROLES);
    }

    @Test
    void freshDatabaseEndsWithTheCurrentTeamShownAsInstalled() {
        migrate();

        upgrade.upgrade();

        assertThat(drafts()).isEqualTo(loader.teamTemplate("team.dev-team").roles());
        var entry = teamEntry();
        assertThat(entry.status()).isEqualTo(AuthorStatus.INSTALLED);
        assertThat(entry.replacesExisting()).isFalse();
    }

    @Test
    void editedSeededTeamIsLeftUntouched() {
        migrate();
        jdbc.sql("UPDATE team_role SET prompt = 'my own prompt' WHERE name = 'tester'").update();

        upgrade.upgrade();

        assertThat(drafts()).extracting(RoleDraft::name).containsExactly("architect", "developer", "tester");
        assertThat(drafts().getLast().prompt()).isEqualTo("my own prompt");
        var entry = teamEntry();
        assertThat(entry.status()).isEqualTo(AuthorStatus.NOT_INSTALLED);
        assertThat(entry.replacesExisting()).isTrue();
    }

    @Test
    void secondRunChangesNothing() {
        migrate();
        upgrade.upgrade();
        var roleIds = jdbc.sql("SELECT id FROM team_role ORDER BY id").query(Long.class).list();
        var decidedAt = jdbc.sql("SELECT decided_at FROM author_item_decision WHERE item_id = 'team.dev-team'").query(String.class).single();

        upgrade.upgrade();

        assertThat(jdbc.sql("SELECT id FROM team_role ORDER BY id").query(Long.class).list()).isEqualTo(roleIds);
        assertThat(jdbc.sql("SELECT decided_at FROM author_item_decision WHERE item_id = 'team.dev-team'").query(String.class).single())
                .isEqualTo(decidedAt);
    }

    @Test
    void missingTeamStaysMissing() {
        migrate();
        teams.delete(teams.list().getFirst().id());

        upgrade.upgrade();

        assertThat(teams.list()).isEmpty();
        assertThat(teamEntry().status()).isEqualTo(AuthorStatus.NOT_INSTALLED);
    }

    @Test
    void projectKeepsItsDefaultTeamId() {
        migrate();
        long teamId = teams.list().getFirst().id();
        jdbc.sql("INSERT INTO project (name, path, created_at, team_id) VALUES ('app', '/a', 'now', ?)").param(teamId).update();

        upgrade.upgrade();

        assertThat(teams.list().getFirst().id()).isEqualTo(teamId);
        assertThat(teams.projectDefaults().values()).containsExactly(teamId);
    }
}
