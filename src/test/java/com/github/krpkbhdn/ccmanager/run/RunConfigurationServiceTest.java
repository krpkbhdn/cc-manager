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
