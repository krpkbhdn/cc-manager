package com.github.krpkbhdn.ccmanager.preset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** The real bundled General preset across simulated app restarts. */
class BundledPresetLifecycleTest {

    @TempDir
    Path temp;

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("t.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    private ProjectPresetService start() {
        var service = new ProjectPresetService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new BundledPresetLoader());
        service.syncBundled();
        return service;
    }

    @Test
    void restartingTwiceKeepsOneUntouchedGeneral() {
        var first = start().list().getFirst();
        start();
        var service = start();

        assertThat(service.list()).hasSize(1);
        var general = service.list().getFirst();
        assertThat(general.id()).isEqualTo(first.id());
        assertThat(general.revision()).isEqualTo(first.revision());
        assertThat(general.components()).extracting(PresetComponent::id).containsExactlyElementsOf(first.components().stream().map(PresetComponent::id).toList());
        assertThat(general.components()).hasSize(7);
    }

    @Test
    void deletedGeneralStaysDeletedAcrossRestartsAndRestoreAllBringsItBackFirst() {
        var service = start();
        service.create("Aaa mine", "");
        service.delete(service.list().getFirst().id());

        start();
        service = start();
        assertThat(service.list()).extracting(ProjectPreset::name).containsExactly("Aaa mine");

        service.restoreAll();
        assertThat(service.list()).extracting(ProjectPreset::name).containsExactly("General", "Aaa mine");
        assertThat(service.list().getFirst().components()).hasSize(7);
    }

    @Test
    void restoreAllResetsAnEditedGeneral() {
        var service = start();
        var general = service.list().getFirst();
        service.removeComponent(general.components().getFirst().id());
        assertThat(service.get(general.id()).orElseThrow().edited()).isTrue();

        service.restoreAll();

        var restored = service.get(general.id()).orElseThrow();
        assertThat(restored.edited()).isFalse();
        assertThat(restored.components()).hasSize(7);
    }
}
