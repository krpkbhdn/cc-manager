package com.github.krpkbhdn.ccmanager.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RunConfigurationServiceTest {

    @TempDir
    Path temp;

    RunConfigurationService service;
    JdbcClient jdbc;
    long projectId;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new RunConfigurationService(jdbc);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
    }

    @Test
    void saveCreatesThenReplacesTheConfigurationWithTheSameName() {
        var first = service.save(projectId, " web ", "npm start", temp);
        var second = service.save(projectId, "web", "npm run dev", temp);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.name()).isEqualTo("web");
        assertThat(second.command()).isEqualTo("npm run dev");
        assertThat(service.list(projectId)).hasSize(1);
    }

    @Test
    void deleteByDirectoryRemovesConfigurationsOfAllProjectsInThatDirectoryOnly() throws Exception {
        var other = java.nio.file.Files.createDirectory(temp.resolve("other"));
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('second', ?, 'now')").param(other.toString()).update();
        var secondId = jdbc.sql("SELECT id FROM project WHERE name = 'second'").query(Long.class).single();
        service.save(projectId, "a", "run", other);
        service.save(secondId, "b", "run", other.resolve("..").resolve("other"));
        service.save(projectId, "keep", "run", temp);

        assertThat(service.listByDirectory(other)).extracting(RunConfiguration::name).containsExactly("a", "b");

        service.deleteByDirectory(other);

        assertThat(service.list(projectId)).extracting(RunConfiguration::name).containsExactly("keep");
        assertThat(service.list(secondId)).isEmpty();
    }

    @Test
    void agentWrittenConfigurationNeedsConfirmationUntilItsCommandChanges() {
        var written = service.saveByAgent(projectId, "web", "npm start", temp);
        assertThat(written.confirmed()).isFalse();

        assertThat(service.confirm(written.id(), "npm start", temp)).isTrue();
        assertThat(service.saveByAgent(projectId, "web", "npm start", temp).confirmed()).isTrue();
        assertThat(service.saveByAgent(projectId, "web", "rm -rf x", temp).confirmed()).isFalse();
    }

    @Test
    void confirmRefusesACommandThatChangedAfterItWasShown() {
        var shown = service.saveByAgent(projectId, "web", "npm start", temp);
        service.saveByAgent(projectId, "web", "rm -rf x", temp);

        assertThat(service.confirm(shown.id(), shown.command(), shown.directory())).isFalse();
        assertThat(service.find(shown.id()).orElseThrow().confirmed()).isFalse();
    }

    @Test
    void editsAndSavesFromTheUiConfirmTheConfiguration() {
        var written = service.saveByAgent(projectId, "web", "npm start", temp);

        assertThat(service.update(written.id(), "web", "npm run dev", temp).confirmed()).isTrue();
        assertThat(service.saveByAgent(projectId, "web", "x", temp).confirmed()).isFalse();
        assertThat(service.save(projectId, "web", "y", temp).confirmed()).isTrue();
    }

    @Test
    void listIsOrderedByNameIgnoringCase() {
        service.save(projectId, "beta", "b", temp);
        service.save(projectId, "Alpha", "a", temp);

        assertThat(service.list(projectId)).extracting(RunConfiguration::name).containsExactly("Alpha", "beta");
    }

    @Test
    void updateRenamesButRefusesAnExistingName() {
        var web = service.save(projectId, "web", "a", temp);
        service.save(projectId, "api", "b", temp);

        assertThat(service.update(web.id(), "site", "c", temp).name()).isEqualTo("site");
        assertThatThrownBy(() -> service.update(web.id(), "api", "c", temp))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    @Test
    void concurrentRenameOntoTheSameNameGivesTheAlreadyExistsMessage() {
        var web = service.save(projectId, "web", "a", temp);
        jdbc.sql("CREATE TRIGGER rival BEFORE UPDATE ON run_configuration BEGIN "
                + "INSERT INTO run_configuration (project_id, name, command, directory) VALUES (OLD.project_id, NEW.name, 'x', 'x'); END").update();

        assertThatThrownBy(() -> service.update(web.id(), "site", "c", temp))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    @Test
    void deleteByNameAndById() {
        var web = service.save(projectId, "web", "a", temp);
        service.save(projectId, "api", "b", temp);

        assertThat(service.delete(projectId, "api")).isTrue();
        assertThat(service.delete(projectId, "api")).isFalse();
        service.delete(web.id());
        assertThat(service.list(projectId)).isEmpty();
    }

    @Test
    void deletingTheProjectDeletesItsConfigurations() {
        service.save(projectId, "web", "a", temp);

        jdbc.sql("DELETE FROM project WHERE id = ?").param(projectId).update();

        assertThat(service.list(projectId)).isEmpty();
    }

    @Test
    void subscribersAreNotifiedUntilTheyUnsubscribe() {
        var calls = new AtomicInteger();
        var unsubscribe = service.subscribe(calls::incrementAndGet);

        service.save(projectId, "web", "a", temp);
        unsubscribe.run();
        service.save(projectId, "api", "b", temp);

        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsInvalidInputWithReadableMessages() {
        var longName = "x".repeat(RunConfigurationService.MAX_NAME + 1);

        assertThatThrownBy(() -> service.save(projectId, "  ", "a", temp)).hasMessageContaining("name");
        assertThatThrownBy(() -> service.save(projectId, longName, "a", temp)).hasMessageContaining("name");
        assertThatThrownBy(() -> service.save(projectId, "web", " ", temp)).hasMessageContaining("command");
        assertThatThrownBy(() -> service.save(projectId, "web", "a", Path.of("relative"))).hasMessageContaining("absolute");
        assertThatThrownBy(() -> service.save(projectId, "web", "a", temp.resolve("missing"))).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> service.save(projectId + 99, "web", "a", temp)).hasMessageContaining("project");
    }
}
