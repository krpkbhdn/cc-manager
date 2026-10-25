package com.github.krpkbhdn.ccmanager.extension.mcp;

import static com.github.krpkbhdn.ccmanager.extension.Json.text;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.Json;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads the MCP server declarations from ~/.claude.json (user and local scope) and the project's .mcp.json.
 * Only names, transport and target are kept: env and headers hold secrets and are dropped here. Never writes.
 */
@Component
class McpConfigReader {

    private static final String SERVERS = "mcpServers";
    private static final String STDIO = "stdio";
    private static final String USER_CONFIG_FILE = ".claude.json";
    private static final String PROJECT_CONFIG_FILE = ".mcp.json";
    private static final String PROJECTS = "projects";
    private static final String SETTINGS_FILE = ".claude/settings.json";
    private static final String SETTINGS_LOCAL_FILE = ".claude/settings.local.json";

    private final HostEnvironment host;

    McpConfigReader(HostEnvironment host) {
        this.host = host;
    }

    /** Claude keeps .claude.json in CLAUDE_CONFIG_DIR when that is set, else directly in the home directory. */
    private Path userConfigFile() {
        var configured = host.env("CLAUDE_CONFIG_DIR");
        var directory = configured == null || configured.isBlank() ? host.home() : host.claudeConfigDirectory();
        return directory.resolve(USER_CONFIG_FILE);
    }

    /** User servers, plus local and project ones when a project directory is given. */
    List<McpServerConfig> read(Path projectDirectory) {
        var userConfig = readFile(userConfigFile());
        var servers = new ArrayList<>(servers(userConfig, ExtensionScope.USER, McpApproval.NOT_REQUIRED));
        if (projectDirectory != null) {
            var projectEntry = projectEntry(userConfig, projectDirectory);
            servers.addAll(servers(projectEntry, ExtensionScope.LOCAL, McpApproval.NOT_REQUIRED));
            var approvals = List.of(readFile(projectDirectory.resolve(SETTINGS_LOCAL_FILE)), readFile(projectDirectory.resolve(SETTINGS_FILE)), projectEntry);
            servers.addAll(servers(readFile(projectDirectory.resolve(PROJECT_CONFIG_FILE)), ExtensionScope.PROJECT, approvals));
        }
        return List.copyOf(servers);
    }

    private JsonNode projectEntry(JsonNode userConfig, Path projectDirectory) {
        var wanted = normalise(projectDirectory.toString());
        return userConfig.path(PROJECTS).properties().stream()
                .filter(entry -> normalise(entry.getKey()).equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(Json.parse("{}"));
    }

    private String normalise(String path) {
        var slashed = path.replace('\\', '/').replaceAll("/+$", "");
        return host.windows() ? slashed.toLowerCase(Locale.ROOT) : slashed;
    }

    private static List<McpServerConfig> servers(JsonNode owner, ExtensionScope scope, McpApproval approval) {
        return servers(owner, scope, name -> approval);
    }

    private static List<McpServerConfig> servers(JsonNode owner, ExtensionScope scope, List<JsonNode> approvals) {
        return servers(owner, scope, name -> approval(approvals, name));
    }

    private static List<McpServerConfig> servers(JsonNode owner, ExtensionScope scope, Function<String, McpApproval> approval) {
        return owner.path(SERVERS).properties().stream().map(entry -> {
            var command = text(entry.getValue(), "command");
            var type = text(entry.getValue(), "type");
            return new McpServerConfig(entry.getKey(), scope, type.isEmpty() ? STDIO : type,
                    McpTarget.masked(command.isEmpty() ? text(entry.getValue(), "url") : command), approval.apply(entry.getKey()));
        }).toList();
    }

    /** @param sources approval holders, most specific first: local settings, shared settings, the ~/.claude.json project entry; disabled wins */
    private static McpApproval approval(List<JsonNode> sources, String name) {
        if (sources.stream().anyMatch(source -> names(source.path("disabledMcpjsonServers")).contains(name))) {
            return McpApproval.DISABLED;
        }
        var enabled = sources.stream().anyMatch(source -> names(source.path("enabledMcpjsonServers")).contains(name));
        var enableAll = sources.stream().map(source -> source.path("enableAllProjectMcpServers")).filter(JsonNode::isBoolean)
                .findFirst().map(JsonNode::asBoolean).orElse(false);
        return enabled || enableAll ? McpApproval.APPROVED : McpApproval.PENDING;
    }

    private static Set<String> names(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(node -> node.asString("")).collect(Collectors.toSet());
    }

    private static JsonNode readFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return Json.parse("{}");
        }
        try {
            return Json.parse(Files.readString(file));
        } catch (IOException e) {
            throw new ExtensionException("Cannot read " + file.getFileName() + ": " + e.getMessage());
        }
    }
}
