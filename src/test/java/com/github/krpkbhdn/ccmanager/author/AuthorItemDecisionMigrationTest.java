package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthorItemDecisionMigrationTest {

    @TempDir
    Path temp;

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("upgrade.db"));
        Flyway.configure().dataSource(dataSource).target("17").load().migrate();
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DELETE FROM author_decision").update();
    }

    void migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    int count(String where) {
        return jdbc.sql("SELECT COUNT(*) FROM author_item_decision WHERE " + where).query(Integer.class).single();
    }

    @Test
    void userSetupExpandsToItsItems() {
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('USER_SETUP', 0, 'DECLINED', 4)").update();

        migrate();

        assertThat(count("status = 'DECLINED' AND version = 1 AND item_id LIKE 'plugin.%'")).isEqualTo(9);
        assertThat(count("item_id IN ('skill.dry-refactoring', 'user.settings.attribution', 'marketplace.ponytail')"))
                .isEqualTo(3);
        assertThat(count("item_id = 'claude-code-skills'")).isZero();
    }

    @Test
    void projectSetupIsDropped() {
        jdbc.sql("INSERT INTO project (id, name, path) VALUES (3, 'a', '/a')").update();
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('PROJECT_SETUP', 3, 'INSTALLED', 2)").update();

        migrate();

        assertThat(count("item_id LIKE 'project.%'")).isZero();
    }

    @Test
    void teamsOlderThanVersionThreeShowsAsUpdate() {
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('TEAMS', 0, 'INSTALLED', 2)").update();

        migrate();

        assertThat(count("item_id = 'team.dev-team' AND status = 'INSTALLED' AND version = 0")).isEqualTo(1);
    }

    @Test
    void teamsOfVersionThreeIsCurrent() {
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('TEAMS', 0, 'INSTALLED', 3)").update();

        migrate();

        var catalogVersion = new AuthorProfileLoader().catalog().byId("team.dev-team").orElseThrow().version();
        var stored = jdbc.sql("SELECT status, version FROM author_item_decision WHERE item_id = 'team.dev-team'")
                .query((rs, n) -> new AuthorDecision("team.dev-team", AuthorChoice.valueOf(rs.getString("status")), rs.getInt("version"))).single();
        assertThat(stored.version()).isEqualTo(3);
        assertThat(AuthorStatus.of(Optional.of(stored), catalogVersion)).isEqualTo(AuthorStatus.INSTALLED);
    }

    @Test
    void oldTableIsDropped() {
        migrate();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM sqlite_master WHERE name = 'author_decision'").query(Integer.class).single()).isZero();
    }
}
