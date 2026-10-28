package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.CcManagerApplication;
import com.github.krpkbhdn.ccmanager.app.mcp.AssistantMcpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.hook.SessionFiles;
import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Boots the real Spring context on a temp data dir and drives the Assistant tools over JSON-RPC. */
class AssistantContextBootTest {

    private static ConfigurableApplicationContext context;
    private static AssistantMcpEndpoint endpoint;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static int nextId = 1;

    @BeforeAll
    static void boot(@TempDir Path dataDir) {
        context = new SpringApplicationBuilder(CcManagerApplication.class).web(WebApplicationType.NONE).headless(true)
                .run("--cc-manager.data-dir=" + dataDir.toAbsolutePath());
        assertThat(context.getBean(AppProperties.class).dataDir()).isEqualTo(dataDir.toAbsolutePath());
        endpoint = context.getBean(AssistantMcpEndpoint.class);
    }

    @AfterAll
    static void close() {
        context.close();
    }

    private static JsonNode rpc(String method, String params) {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":" + nextId++ + ",\"method\":\"" + method + "\",\"params\":" + params + "}";
        return MAPPER.readTree(endpoint.handle(body.getBytes(StandardCharsets.UTF_8)).orElseThrow());
    }

    private static String call(String tool, String arguments) {
        var result = rpc("tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}").path("result");
        return (result.path("isError").asBoolean() ? "ERROR: " : "") + result.path("content").get(0).path("text").asString();
    }

    @Test
    void everyToolHasDescriptionAndObjectSchemaAndNamesAreUnique() {
        var tools = rpc("tools/list", "{}").path("result").path("tools");
        var names = new HashSet<String>();
        assertThat(tools.size()).isGreaterThan(45);
        for (var tool : tools) {
            var name = tool.path("name").asString();
            assertThat(names.add(name)).as("duplicate " + name).isTrue();
            assertThat(tool.path("description").asString()).as(name).isNotBlank();
            assertThat(tool.path("inputSchema").path("type").asString()).as(name).isEqualTo("object");
        }
        assertThat(names).contains("resume_session", "set_team", "list_teams", "delete_team", "set_prompt", "list_prompts", "get_prompt",
                "delete_prompt", "list_skills", "get_skill", "set_skill", "delete_skill", "list_plugins", "get_plugin", "install_plugin",
                "uninstall_plugin", "set_plugin_enabled", "add_marketplace", "remove_marketplace", "list_mcp_servers", "get_mcp_server",
                "add_mcp_server", "remove_mcp_server", "set_group", "start_session", "queue_prompt", "list_sessions", "remove_project", "get_help");
    }

    @Test
    void allowedToolsFollowAutoApprovedAndExcludeDangerousOnes() {
        var allowed = endpoint.autoApprovedToolNames();
        assertThat(allowed).contains("set_prompt", "list_teams", "get_help", "resume_session", "list_plugins", "get_mcp_server")
                .doesNotContain("delete_prompt", "delete_team", "set_skill", "delete_skill", "install_plugin", "uninstall_plugin",
                        "add_mcp_server", "remove_mcp_server", "remove_project", "set_team", "git_commit", "git_push");
        var arguments = ClaudeCommand.assistantArguments(Path.of("claude"), new SessionFiles(Path.of("s.json"), Path.of("m.json")),
                ModelSelection.DEFAULT, allowed);
        var list = arguments.get(arguments.indexOf("--allowedTools") + 1);
        assertThat(List.of(list.split(","))).hasSize(allowed.size()).allMatch(n -> n.startsWith("mcp__cc-manager-app__"));
        assertThat(context.getBean(ClaudeLauncher.class)).isNotNull();
    }

    @Test
    void promptsRoundTrip() {
        assertThat(call("set_prompt", "{\"name\":\"p1\",\"text\":\"hello {project}\"}")).contains("p1");
        var id = MAPPER.readTree(call("list_prompts", "{}")).findValues("id").stream().mapToLong(JsonNode::asLong).max().orElseThrow();
        assertThat(call("get_prompt", "{\"id\":" + id + "}")).contains("hello {project}");
        assertThat(call("delete_prompt", "{\"id\":" + id + "}")).doesNotStartWith("ERROR");
        assertThat(MAPPER.readTree(call("list_prompts", "{}")).findValuesAsString("name")).doesNotContain("p1");
    }

    @Test
    void teamsRoundTrip() {
        var roles = "[{\"name\":\"boss\",\"description\":\"leads\",\"prompt\":\"lead it\",\"lead\":true},"
                + "{\"name\":\"coder\",\"description\":\"codes\",\"prompt\":\"code it\"}]";
        assertThat(call("set_team", "{\"name\":\"T9\",\"roles\":" + roles + "}")).contains("2 role");
        var teams = MAPPER.readTree(call("list_teams", "{}"));
        long id = -1;
        for (var t : teams) {
            if (t.path("name").asString().equals("T9")) {
                id = t.path("id").asLong();
            }
        }
        assertThat(id).isPositive();
        assertThat(call("delete_team", "{\"id\":" + id + "}")).doesNotStartWith("ERROR");
        assertThat(MAPPER.readTree(call("list_teams", "{}")).findValuesAsString("name")).doesNotContain("T9");
    }

    @Test
    void groupWithMembersIsReadLikeAProject(@TempDir Path root) throws Exception {
        var projects = context.getBean(ProjectService.class);
        var a = projects.add(Files.createDirectory(root.resolve("a")));
        var b = projects.add(Files.createDirectory(root.resolve("b")));
        var created = call("set_group", "{\"name\":\"G1\",\"members\":[" + a.id() + "," + b.id() + "]}");
        assertThat(created).contains("2 member");
        long gid = -1;
        for (var p : MAPPER.readTree(call("list_projects", "{}"))) {
            if (p.path("name").asString().equals("G1")) {
                gid = p.path("id").asLong();
            }
        }
        var detail = call("get_project", "{\"project\":" + gid + "}");
        assertThat(detail).doesNotStartWith("ERROR").contains("members");
        assertThat(call("list_sessions", "{\"project\":" + gid + "}")).doesNotStartWith("ERROR");
    }

    @Test
    void helpWithAndWithoutTopicAndUnknownTopic() {
        var index = call("get_help", "{}");
        assertThat(index).contains("topic");
        var firstTopic = index.substring(index.indexOf(':') + 1).split(",")[0].strip().replaceAll("\\.$", "");
        assertThat(call("get_help", "{\"topic\":\"" + firstTopic + "\"}")).doesNotStartWith("ERROR").isNotBlank();
        assertThat(call("get_help", "{\"topic\":\"nope-xyz\"}")).startsWith("ERROR").contains("Unknown topic");
    }
}
