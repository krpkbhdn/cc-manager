package com.github.krpkbhdn.ccmanager.preset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PresetApplicationServiceTest {

    @TempDir
    Path temp;

    JdbcClient jdbc;
    PresetApplicationService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new PresetApplicationService(jdbc);
    }

    private long preset(String name, int revision, boolean deleted) {
        jdbc.sql("INSERT INTO project_preset (name, description, revision, deleted) VALUES (?, '', ?, ?)")
                .param(name).param(revision).param(deleted ? 1 : 0).update();
        return jdbc.sql("SELECT id FROM project_preset WHERE name = ?").param(name).query(Long.class).single();
    }

    @Test
    void noApplicationsForAProjectThatAppliedNothing() {
        assertThat(service.applications(1)).isEmpty();
        assertThat(service.outdated(1)).isEmpty();
    }

    @Test
    void markAppliedRecordsTheRevisionAndRepeatedMarkingOverwritesIt() {
        var id = preset("p", 1, false);

        service.markApplied(1, id, 1);
        service.markApplied(1, id, 3);

        assertThat(service.applications(1)).containsExactly(Map.entry(id, 3));
    }

    @Test
    void applicationsAreKeptPerProject() {
        var id = preset("p", 1, false);

        service.markApplied(1, id, 1);

        assertThat(service.applications(2)).isEmpty();
    }

    @Test
    void presetWithANewerRevisionIsOutdatedAndAnUpToDateOneIsNot() {
        var stale = preset("stale", 1, false);
        var fresh = preset("fresh", 1, false);
        service.markApplied(1, stale, 1);
        service.markApplied(1, fresh, 1);

        jdbc.sql("UPDATE project_preset SET revision = 2 WHERE id = ?").param(stale).update();

        assertThat(service.outdated(1)).containsExactly(stale);
    }

    @Test
    void reapplyingTheNewRevisionClearsOutdated() {
        var id = preset("p", 2, false);
        service.markApplied(1, id, 1);
        assertThat(service.outdated(1)).containsExactly(id);

        service.markApplied(1, id, 2);

        assertThat(service.outdated(1)).isEmpty();
    }

    @Test
    void deletedPresetIsNeverOutdated() {
        var id = preset("gone", 5, true);
        service.markApplied(1, id, 1);

        assertThat(service.outdated(1)).isEmpty();
    }

    @Test
    void declinedOfferIsRememberedPerProjectAndDecliningTwiceIsHarmless() {
        assertThat(service.offerDeclined(1)).isFalse();

        service.declineOffer(1);
        service.declineOffer(1);

        assertThat(service.offerDeclined(1)).isTrue();
        assertThat(service.offerDeclined(2)).isFalse();
    }
}
