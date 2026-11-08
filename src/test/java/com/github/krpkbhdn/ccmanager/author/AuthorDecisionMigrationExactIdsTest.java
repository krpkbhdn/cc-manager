package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthorDecisionMigrationExactIdsTest {

    @TempDir
    Path temp;

    @Test
    void v17ToLatestKeepsExactUserIdsDropsProjectRowsAndProjectDeleteStillWorks() {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("m.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(ds).target("17").load().migrate();
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("DELETE FROM author_decision").update();
        jdbc.sql("INSERT INTO project (id, name, path) VALUES (3, 'a', '/a')").update();
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('USER_SETUP', 0, 'INSTALLED', 4)").update();
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('TEAMS', 0, 'INSTALLED', 2)").update();
        jdbc.sql("INSERT INTO author_decision (component, project_id, status, version) VALUES ('PROJECT_SETUP', 3, 'DECLINED', 2)").update();

        Flyway.configure().dataSource(ds).load().migrate();

        var ids = jdbc.sql("SELECT item_id FROM author_item_decision").query(String.class).list();
        assertThat(ids).containsExactlyInAnyOrder("marketplace.claude-plugins-official", "marketplace.ponytail", "plugin.frontend-design",
                "plugin.context7", "plugin.playwright", "plugin.claude-md-management", "plugin.code-review", "plugin.code-simplifier",
                "plugin.feature-dev", "plugin.postman", "plugin.ponytail", "skill.jscpd", "skill.dry-refactoring", "user.settings.attribution", "team.dev-team");
        assertThat(jdbc.sql("DELETE FROM project WHERE id = 3").update()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM project").query(Integer.class).single()).isZero();
    }
}
