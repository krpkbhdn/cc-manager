package com.github.krpkbhdn.ccmanager.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ProjectGroupMigrationTest {

    @TempDir
    Path temp;

    @Test
    void upgradeFromV9KeepsExistingProjectsAsUngroupedNonGroups() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("old.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).target("9").load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('old', :p, '2026-01-01T00:00:00Z')").param("p", temp.resolve("old").toString()).update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        var project = new ProjectService(jdbc, new GroupWorkspace(new AppProperties(temp))).list().getFirst();
        assertThat(project.name()).isEqualTo("old");
        assertThat(project.group()).isFalse();
        assertThat(project.groupId()).isNull();
    }

    @Test
    void databaseMigratedByAnotherBranchWithItsOwnV10UpgradesToHead() throws IOException {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("other.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).target("9").load().migrate();
        var foreign = Files.createDirectory(temp.resolve("foreign"));
        Files.writeString(foreign.resolve("V10__create_glpi.sql"), "CREATE TABLE glpi_connection (id INTEGER PRIMARY KEY);");
        Flyway.configure().dataSource(dataSource).locations("filesystem:" + foreign)
                .ignoreMigrationPatterns("*:missing").load().migrate();

        Flyway.configure().dataSource(dataSource).ignoreMigrationPatterns("*:missing").load().migrate();

        var columns = JdbcClient.create(dataSource).sql("SELECT name FROM pragma_table_info('project')").query(String.class).list();
        assertThat(columns).contains("is_group", "group_id");
    }

    @Test
    void removingMemberProjectLeavesGroupWithRemainingMembers() throws IOException {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("t.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        var projects = new ProjectService(jdbc, new GroupWorkspace(new AppProperties(temp)));
        var groups = new ProjectGroupService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                projects, new GroupWorkspace(new AppProperties(temp)));
        var a = projects.add(Files.createDirectory(temp.resolve("a")));
        var b = projects.add(Files.createDirectory(temp.resolve("b")));
        var group = groups.create("g", List.of(a.id(), b.id()));

        projects.remove(a.id());

        assertThat(groups.members(group.id())).extracting(Project::id).containsExactly(b.id());
        assertThat(projects.find(group.id())).isPresent();
    }
}
