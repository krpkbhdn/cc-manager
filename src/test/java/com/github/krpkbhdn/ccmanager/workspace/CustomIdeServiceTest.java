package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CustomIdeServiceTest {

    @TempDir
    Path temp;

    CustomIdeService service;
    Path executable;

    @BeforeEach
    void setUp() throws IOException {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        service = new CustomIdeService(JdbcClient.create(dataSource));
        executable = Files.createFile(temp.resolve("myide.exe"));
    }

    @Test
    void createStoresTrimmedValuesAndBuildsTheCommandWithoutAShell() {
        var created = service.create(" Mine ", executable.toString(), " --new-window  --wait ");

        assertThat(created.name()).isEqualTo("Mine");
        assertThat(created.ideId()).isEqualTo("custom-" + created.id());
        assertThat(created.command()).containsExactly(executable.toString(), "--new-window", "--wait");
        assertThat(service.list()).containsExactly(created);
    }

    @Test
    void blankArgumentsGiveJustTheExecutable() {
        assertThat(service.create("Mine", executable.toString(), "").command()).containsExactly(executable.toString());
    }

    @Test
    void duplicateNameIgnoringCaseIsRejectedButUpdatingItselfIsAllowed() {
        var created = service.create("Mine", executable.toString(), "");

        assertThatThrownBy(() -> service.create("mine", executable.toString(), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        assertThat(service.update(created.id(), "MINE", executable.toString(), "-x").arguments()).isEqualTo("-x");
    }

    @Test
    void blankNameMissingFileAndDirectoryAreRejected() {
        assertThatThrownBy(() -> service.create(" ", executable.toString(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("A", temp.resolve("missing.exe").toString(), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("executable");
        assertThatThrownBy(() -> service.create("B", temp.toString(), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("executable");
        assertThatThrownBy(() -> service.create("C", "", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateOfUnknownIdFailsAndDeleteRemovesTheRow() {
        assertThatThrownBy(() -> service.update(99, "A", executable.toString(), "")).isInstanceOf(IllegalArgumentException.class);
        var created = service.create("Mine", executable.toString(), "");

        service.delete(created.id());

        assertThat(service.find(created.id())).isEmpty();
    }

    @Test
    void listenersFireOnEveryChangeUntilUnsubscribed() {
        var fired = new AtomicInteger();
        var unsubscribe = service.subscribe(fired::incrementAndGet);

        var created = service.create("Mine", executable.toString(), "");
        service.update(created.id(), "Mine2", executable.toString(), "");
        service.delete(created.id());
        unsubscribe.run();
        service.create("Again", executable.toString(), "");

        assertThat(fired).hasValue(3);
    }
}
