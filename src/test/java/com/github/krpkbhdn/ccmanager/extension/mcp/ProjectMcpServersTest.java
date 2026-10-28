package com.github.krpkbhdn.ccmanager.extension.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.git.GitRunner;
import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectMcpServersTest {

    private static final String APPROVE_OK = "{\"enabledMcpjsonServers\":[\"ok\"]}";

    @TempDir
    Path home;

    @TempDir
    Path first;

    @TempDir
    Path second;

    private ProjectMcpServers servers() {
        return new ProjectMcpServers(new McpConfigReader(new FakeHostEnvironment(HostEnvironment.OperatingSystem.LINUX, home), new GitRunner()));
    }

    private static String json(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    private static String trusted(Path project, boolean trusted) {
        return "\"" + json(project) + "\":{\"hasTrustDialogAccepted\":" + trusted + "}";
    }

    private void userConfig(String... entries) throws IOException {
        Files.writeString(home.resolve(".claude.json"), "{\"projects\":{" + String.join(",", entries) + "}}");
    }

    private static void mcpJson(Path project, String content) throws IOException {
        Files.writeString(project.resolve(".mcp.json"), content);
    }

    private static void localSettings(Path project, String settings) throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".claude/settings.local.json"), settings);
    }

    private void userSettings(String settings) throws IOException {
        Files.createDirectories(home.resolve(".claude"));
        Files.writeString(home.resolve(".claude/settings.json"), settings);
    }

    private static void git(Path project, String... args) {
        new GitRunner().out(project, args);
    }

    private static void approveOkServer(Path project) throws IOException {
        mcpJson(project, "{\"mcpServers\":{\"ok\":{\"command\":\"a\"}}}");
        localSettings(project, APPROVE_OK);
    }

    @Test
    void copiesOnlyServersApprovedInTheMembersLocalSettingsWithTheirFullDefinition() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"ok\":{\"command\":\"a\",\"env\":{\"K\":\"v\"}},\"new\":{\"command\":\"b\"}}}");
        localSettings(first, APPROVE_OK);
        userConfig(trusted(first, true));

        var approved = servers().approved(List.of(first));

        assertThat(approved).containsOnlyKeys("ok");
        assertThat(approved.get("ok").toString()).isEqualTo("{\"command\":\"a\",\"env\":{\"K\":\"v\"}}");
    }

    @Test
    void enableAllInLocalSettingsApprovesEveryServer() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"a\":{\"command\":\"a\"},\"b\":{\"command\":\"b\"}}}");
        localSettings(first, "{\"enableAllProjectMcpServers\":true}");
        userConfig(trusted(first, true));

        assertThat(servers().approved(List.of(first))).containsOnlyKeys("a", "b");
    }

    @Test
    void repoSharedSettingsAreNeverReadForApproval() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"ok\":{\"command\":\"a\"}}}");
        Files.createDirectories(first.resolve(".claude"));
        Files.writeString(first.resolve(".claude/settings.json"), APPROVE_OK);
        userConfig(trusted(first, true));

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void localSettingsTrackedByGitAreIgnored() throws IOException {
        approveOkServer(first);
        userConfig(trusted(first, true));
        git(first, "init");
        git(first, "add", "-f", ".claude/settings.local.json");

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void untrackedLocalSettingsInAGitRepositoryAreHonoured() throws IOException {
        approveOkServer(first);
        userConfig(trusted(first, true));
        git(first, "init");

        assertThat(servers().approved(List.of(first))).containsOnlyKeys("ok");
    }

    @Test
    void disabledInLocalSettingsWinsOverUserSettingsApproval() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"ok\":{\"command\":\"a\"}}}");
        localSettings(first, "{\"disabledMcpjsonServers\":[\"ok\"]}");
        userSettings(APPROVE_OK);
        userConfig(trusted(first, true));

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void disabledInUserSettingsWinsOverLocalApproval() throws IOException {
        approveOkServer(first);
        userSettings("{\"disabledMcpjsonServers\":[\"ok\"]}");
        userConfig(trusted(first, true));

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void userSettingsApprovalCountsForATrustedMember() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"ok\":{\"command\":\"a\"}}}");
        userSettings(APPROVE_OK);
        userConfig(trusted(first, true));

        assertThat(servers().approved(List.of(first))).containsOnlyKeys("ok");
    }

    @Test
    void untrustedMemberIsSkippedEvenWhenApproved() throws IOException {
        approveOkServer(first);
        userConfig(trusted(first, false));

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void memberWithoutAnEntryInTheUsersConfigIsSkipped() throws IOException {
        approveOkServer(first);

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void trustKeyInForwardSlashFormIsFound() throws IOException {
        approveOkServer(first);
        userConfig("\"" + first.toString().replace('\\', '/') + "\":{\"hasTrustDialogAccepted\":true}");

        assertThat(servers().approved(List.of(first))).containsOnlyKeys("ok");
    }

    @Test
    void firstMemberWinsOnDuplicateNames() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"db\":{\"command\":\"one\"}}}");
        mcpJson(second, "{\"mcpServers\":{\"db\":{\"command\":\"two\"},\"web\":{\"command\":\"w\"}}}");
        localSettings(first, "{\"enabledMcpjsonServers\":[\"db\"]}");
        localSettings(second, "{\"enabledMcpjsonServers\":[\"db\",\"web\"]}");
        userConfig(trusted(first, true), trusted(second, true));

        var approved = servers().approved(List.of(first, second));

        assertThat(approved).containsOnlyKeys("db", "web");
        assertThat(approved.get("db").path("command").asString()).isEqualTo("one");
    }

    @Test
    void malformedFilesOfOneMemberDoNotBlockTheOthers() throws IOException {
        mcpJson(first, "{not json");
        localSettings(first, "{\"enabledMcpjsonServers\":[\"x\"]}");
        mcpJson(second, "{\"mcpServers\":{\"web\":{\"command\":\"w\"}}}");
        localSettings(second, "{\"enabledMcpjsonServers\":[\"web\"]}");
        userConfig(trusted(first, true), trusted(second, true));

        assertThat(servers().approved(List.of(first, second))).containsOnlyKeys("web");
    }

    @Test
    void malformedLocalSettingsSkipOnlyThatMember() throws IOException {
        approveOkServer(first);
        localSettings(first, "{broken");
        approveOkServer(second);
        userConfig(trusted(first, true), trusted(second, true));

        assertThat(servers().approved(List.of(first, second))).containsOnlyKeys("ok");
    }

    @Test
    void malformedUsersConfigSkipsEveryServerInsteadOfFailing() throws IOException {
        approveOkServer(first);
        Files.writeString(home.resolve(".claude.json"), "{broken");

        assertThat(servers().approved(List.of(first))).isEmpty();
    }

    @Test
    void relativeCommandAndDotPrefixedArgumentsBecomeAbsoluteAgainstTheMemberRoot() throws IOException {
        mcpJson(first, "{\"mcpServers\":{\"ok\":{\"command\":\"./bin/run\",\"args\":[\"./x\",\"--port\",\".\\\\y\"]}}}");
        localSettings(first, APPROVE_OK);
        userConfig(trusted(first, true));

        var server = servers().approved(List.of(first)).get("ok");

        assertThat(Path.of(server.path("command").asString())).isEqualTo(first.resolve("bin/run").toAbsolutePath().normalize());
        assertThat(Path.of(server.path("args").get(0).asString())).isEqualTo(first.resolve("x").toAbsolutePath().normalize());
        assertThat(server.path("args").get(1).asString()).isEqualTo("--port");
    }

    @Test
    void urlsConnectionStringsBareWordsAndEmptyArgumentsStayUntouched() throws IOException {
        Files.createDirectory(first.resolve("my_server"));
        mcpJson(first, "{\"mcpServers\":{\"ok\":{\"command\":\"python\",\"args\":[\"-m\",\"my_server\",\"https://x\","
                + "\"postgresql://user@h/db\",\"a:b\",\"?*\",\"\",\"../escape\"]}}}");
        localSettings(first, APPROVE_OK);
        userConfig(trusted(first, true));

        var server = servers().approved(List.of(first)).get("ok");

        assertThat(server.path("args").valueStream().map(arg -> arg.asString()).toList())
                .containsExactly("-m", "my_server", "https://x", "postgresql://user@h/db", "a:b", "?*", "", "../escape");
    }

    @Test
    void memberWithoutMcpJsonContributesNothing() throws IOException {
        localSettings(first, APPROVE_OK);
        userConfig(trusted(first, true));

        assertThat(servers().approved(List.of(first))).isEmpty();
    }
}
