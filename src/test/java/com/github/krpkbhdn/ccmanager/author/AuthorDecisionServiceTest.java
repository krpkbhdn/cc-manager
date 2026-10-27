package com.github.krpkbhdn.ccmanager.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthorDecisionServiceTest {

    @TempDir
    Path temp;

    AuthorDecisionService service;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new AuthorDecisionService(jdbc);
    }

    @Test
    void migrationDropsTheDecisionSeededForTheDevTeam() {
        assertThat(service.find(AuthorComponent.TEAMS, 0)).isEmpty();
    }

    @Test
    void migrationKeepsATeamsDecisionOfALaterVersion() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("upgrade.db"));
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        var upgrade = JdbcClient.create(dataSource);
        upgrade.sql("UPDATE author_decision SET version = 2 WHERE component = 'TEAMS'").update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(new AuthorDecisionService(upgrade).find(AuthorComponent.TEAMS, 0))
                .contains(new AuthorDecision(AuthorComponent.TEAMS, 0, AuthorChoice.INSTALLED, 2));
    }

    @Test
    void unknownComponentHasNoDecision() {
        assertThat(service.find(AuthorComponent.USER_SETUP, 0)).isEmpty();
    }

    @Test
    void recordStoresTheDecisionPerProject() {
        service.record(AuthorComponent.PROJECT_SETUP, 7, AuthorChoice.DECLINED, 1);

        assertThat(service.find(AuthorComponent.PROJECT_SETUP, 7)).contains(
                new AuthorDecision(AuthorComponent.PROJECT_SETUP, 7, AuthorChoice.DECLINED, 1));
        assertThat(service.find(AuthorComponent.PROJECT_SETUP, 8)).isEmpty();
    }

    @Test
    void installingAfterDecliningOverwritesTheDecline() {
        service.record(AuthorComponent.USER_SETUP, 0, AuthorChoice.DECLINED, 1);
        service.record(AuthorComponent.USER_SETUP, 0, AuthorChoice.INSTALLED, 2);

        assertThat(service.find(AuthorComponent.USER_SETUP, 0)).contains(
                new AuthorDecision(AuthorComponent.USER_SETUP, 0, AuthorChoice.INSTALLED, 2));
    }

    @Test
    void removingAProjectDropsItsDecisions() {
        jdbc.sql("INSERT INTO project (id, name, path) VALUES (5, 'p', '/p')").update();
        service.record(AuthorComponent.PROJECT_SETUP, 5, AuthorChoice.INSTALLED, 1);
        service.record(AuthorComponent.USER_SETUP, 0, AuthorChoice.INSTALLED, 1);

        jdbc.sql("DELETE FROM project WHERE id = 5").update();

        assertThat(service.find(AuthorComponent.PROJECT_SETUP, 5)).isEmpty();
        assertThat(service.find(AuthorComponent.USER_SETUP, 0)).isPresent();
    }
}
