package com.github.krpkbhdn.ccmanager.project;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
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

class ProjectServiceTest {

    @TempDir
    Path temp;

    ProjectService service;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new ProjectService(jdbc, new GroupWorkspace(new AppProperties(temp)));
    }

    private Path directory(String name) throws IOException {
        return Files.createDirectory(temp.resolve(name));
    }

    @Test
    void addStoresRealPathWithDirectoryNameAsDefaultName() throws IOException {
        var project = service.add(directory("alpha"));

        assertThat(project.name()).isEqualTo("alpha");
        assertThat(project.path()).isEqualTo(temp.resolve("alpha").toRealPath());
        assertThat(service.find(project.id())).contains(project);
        assertThat(service.list()).containsExactly(project);
    }

    @Test
    void storedEffortThatFailsCurrentRulesFallsBackToDefault() throws IOException {
        var project = service.add(directory("alpha"));
        jdbc.sql("UPDATE project SET effort = 'retired' WHERE id = ?").param(project.id()).update();

        assertThat(service.list()).extracting(Project::modelSelection).containsExactly(ModelSelection.DEFAULT);
    }

    @Test
    void staleEffortKeepsAValidStoredModel() throws IOException {
        var project = service.add(directory("alpha"));
        jdbc.sql("UPDATE project SET model = 'opus', effort = 'retired' WHERE id = ?").param(project.id()).update();

        assertThat(service.list()).extracting(Project::modelSelection).containsExactly(new ModelSelection("opus", null));
    }

    @Test
    void addRejectsMissingDirectoryFileAndDuplicate() throws IOException {
        var dir = directory("alpha");
        var file = Files.createFile(temp.resolve("file.txt"));
        service.add(dir);

        assertThatThrownBy(() -> service.add(temp.resolve("missing"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> service.add(file)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Not a directory");
        assertThatThrownBy(() -> service.add(dir.resolve("..").resolve("alpha"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already added");
    }

    @Test
    void renameAndRemoveUpdateStorage() throws IOException {
        var project = service.add(directory("alpha"));

        assertThat(service.rename(project.id(), "  Beta ").name()).isEqualTo("Beta");
        assertThatThrownBy(() -> service.rename(project.id(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rename(999, "x")).isInstanceOf(IllegalArgumentException.class);

        service.remove(project.id());
        assertThat(service.list()).isEmpty();
    }

    @Test
    void modelSelectionIsStoredPerProjectAndClearable() throws IOException {
        var project = service.add(directory("alpha"));
        assertThat(project.modelSelection()).isEqualTo(ModelSelection.DEFAULT);

        var chosen = service.selectModel(project.id(), new ModelSelection("opus", "xhigh"));
        assertThat(service.find(project.id()).orElseThrow().modelSelection()).isEqualTo(new ModelSelection("opus", "xhigh")).isEqualTo(chosen.modelSelection());

        assertThat(service.selectModel(project.id(), ModelSelection.DEFAULT).modelSelection()).isEqualTo(ModelSelection.DEFAULT);
        assertThatThrownBy(() -> service.selectModel(999, ModelSelection.DEFAULT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subscribersAreNotifiedUntilUnsubscribed() throws IOException {
        var calls = new AtomicInteger();
        var unsubscribe = service.subscribe(calls::incrementAndGet);

        var project = service.add(directory("alpha"));
        service.rename(project.id(), "b");
        service.remove(project.id());
        assertThat(calls).hasValue(3);

        unsubscribe.run();
        service.add(directory("gamma"));
        assertThat(calls).hasValue(3);
    }

    @Test
    void failingListenerDoesNotBreakOthers() throws IOException {
        var calls = new AtomicInteger();
        service.subscribe(() -> { throw new IllegalStateException("boom"); });
        service.subscribe(calls::incrementAndGet);

        service.add(directory("alpha"));

        assertThat(calls).hasValue(1);
    }
}
