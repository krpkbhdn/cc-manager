package com.github.krpkbhdn.ccmanager.prompt;

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

class PromptTemplateServiceTest {

    @TempDir
    Path temp;

    PromptTemplateService service;
    JdbcClient jdbc;
    long projectId;
    long teamId;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new PromptTemplateService(jdbc);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        teamId = jdbc.sql("SELECT id FROM team LIMIT 1").query(Long.class).single();
    }

    @Test
    void createStoresTheTemplateWithTrimmedName() {
        var created = service.create(" Review ", "Review {branch}", projectId, teamId);

        assertThat(created.name()).isEqualTo("Review");
        assertThat(created.projectId()).isEqualTo(projectId);
        assertThat(created.defaultTeamId()).isEqualTo(teamId);
        assertThat(service.find(created.id())).contains(created);
    }

    @Test
    void listForReturnsGlobalAndOwnTemplatesOnly() {
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('other', ?, 'now')").param(temp.resolve("other").toString()).update();
        var otherId = jdbc.sql("SELECT id FROM project WHERE name = 'other'").query(Long.class).single();
        service.create("b global", "t", null, null);
        service.create("a own", "t", projectId, null);
        service.create("c foreign", "t", otherId, null);

        assertThat(service.listFor(projectId)).extracting(PromptTemplate::name).containsExactly("a own", "b global");
        assertThat(service.list()).hasSize(3);
    }

    @Test
    void updateReplacesFieldsAndRejectsUnknownId() {
        var created = service.create("a", "t", null, null);

        var updated = service.update(created.id(), "b", "new", projectId, null);

        assertThat(updated.name()).isEqualTo("b");
        assertThat(updated.projectId()).isEqualTo(projectId);
        assertThatThrownBy(() -> service.update(999, "x", "t", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidInputIsRejectedWithAMessage() {
        assertThatThrownBy(() -> service.create(" ", "t", null, null)).hasMessageContaining("name");
        assertThatThrownBy(() -> service.create("a", " ", null, null)).hasMessageContaining("text");
        assertThatThrownBy(() -> service.create("a", "t", 999L, null)).hasMessageContaining("project");
        assertThatThrownBy(() -> service.create("a", "t", null, 999L)).hasMessageContaining("team");
        assertThatThrownBy(() -> service.create("x".repeat(PromptTemplateService.MAX_NAME + 1), "t", null, null)).hasMessageContaining("name");
    }

    @Test
    void deletingTheProjectRemovesItsTemplatesAndDeletingTheTeamClearsTheDefault() {
        service.create("own", "t", projectId, null);
        var global = service.create("global", "t", null, teamId);

        jdbc.sql("DELETE FROM team WHERE id = ?").param(teamId).update();
        jdbc.sql("DELETE FROM project WHERE id = ?").param(projectId).update();

        assertThat(service.list()).extracting(PromptTemplate::id).containsExactly(global.id());
        assertThat(service.find(global.id()).orElseThrow().defaultTeamId()).isNull();
    }

    @Test
    void subscribersAreNotifiedOnChangesUntilUnsubscribed() {
        var calls = new AtomicInteger();
        var unsubscribe = service.subscribe(calls::incrementAndGet);

        var created = service.create("a", "t", null, null);
        service.delete(created.id());
        service.delete(created.id());
        assertThat(calls).hasValue(2);

        unsubscribe.run();
        service.create("b", "t", null, null);
        assertThat(calls).hasValue(2);
    }
}
