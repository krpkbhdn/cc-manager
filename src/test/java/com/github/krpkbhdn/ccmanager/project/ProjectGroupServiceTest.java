package com.github.krpkbhdn.ccmanager.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class ProjectGroupServiceTest {

    @TempDir
    Path temp;

    ProjectService projects;
    ProjectGroupService groups;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        projects = new ProjectService(jdbc, new GroupWorkspace(new AppProperties(temp)));
        groups = new ProjectGroupService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                projects, new GroupWorkspace(new AppProperties(temp)));
    }

    private Project project(String name) throws IOException {
        return projects.add(Files.createDirectory(temp.resolve(name)));
    }

    @Test
    void createStoresGroupWithMembersAndServiceDirectory() throws IOException {
        var a = project("a");
        var group = groups.create(" Team ", List.of(a.id()));

        assertThat(group.group()).isTrue();
        assertThat(group.name()).isEqualTo("Team");
        assertThat(group.path()).isDirectory().startsWith(temp.resolve("groups"));
        assertThat(groups.members(group.id())).extracting(Project::id).containsExactly(a.id());
    }

    @Test
    void createRejectsBlankName() throws IOException {
        var a = project("a");
        assertThatThrownBy(() -> groups.create(" ", List.of(a.id()))).hasMessageContaining("name");
    }

    @Test
    void createRejectsEmptyMembersAndLeavesNoGroup() {
        assertThatThrownBy(() -> groups.create("g", List.of())).hasMessageContaining("at least one");
        assertThat(projects.list()).isEmpty();
    }

    @Test
    void createRejectsUnknownMember() {
        assertThatThrownBy(() -> groups.create("g", List.of(999L))).hasMessageContaining("not found");
    }

    @Test
    void createRejectsGroupAsMember() throws IOException {
        var inner = groups.create("inner", List.of(project("a").id()));
        assertThatThrownBy(() -> groups.create("outer", List.of(inner.id()))).hasMessageContaining("cannot be a member");
    }

    @Test
    void createRejectsMemberOfAnotherGroup() throws IOException {
        var a = project("a");
        groups.create("one", List.of(a.id()));
        assertThatThrownBy(() -> groups.create("two", List.of(a.id()))).hasMessageContaining("already in another group");
    }

    @Test
    void setMembersReplacesMembersAndFreesRemoved() throws IOException {
        var a = project("a");
        var b = project("b");
        var group = groups.create("g", List.of(a.id()));

        groups.setMembers(group.id(), List.of(b.id()));

        assertThat(groups.members(group.id())).extracting(Project::id).containsExactly(b.id());
        assertThat(projects.find(a.id()).orElseThrow().groupId()).isNull();
    }

    @Test
    void setMembersKeepsCurrentMembersWhenValidationFails() throws IOException {
        var a = project("a");
        var group = groups.create("g", List.of(a.id()));

        assertThatThrownBy(() -> groups.setMembers(group.id(), List.of())).hasMessageContaining("at least one");

        assertThat(groups.members(group.id())).extracting(Project::id).containsExactly(a.id());
    }

    @Test
    void setMembersRejectsNonGroupTarget() throws IOException {
        var a = project("a");
        var b = project("b");
        assertThatThrownBy(() -> groups.setMembers(a.id(), List.of(b.id()))).hasMessageContaining("Not a group");
    }

    @Test
    void removingGroupFreesItsMembers() throws IOException {
        var a = project("a");
        var group = groups.create("g", List.of(a.id()));

        projects.remove(group.id());

        assertThat(projects.find(a.id()).orElseThrow().groupId()).isNull();
        assertThat(group.path()).doesNotExist();
        assertThat(projects.find(a.id())).isPresent();
        assertThat(a.path()).isDirectory();
    }

    @Test
    void failedCreateLeavesNoGroupFolderBehind() {
        assertThatThrownBy(() -> groups.create("g", List.of(999L))).isInstanceOf(IllegalArgumentException.class);

        assertThat(temp.resolve("groups")).isEmptyDirectory();
    }
}
