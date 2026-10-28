package com.github.krpkbhdn.ccmanager.extension.mcp;

import com.github.krpkbhdn.ccmanager.extension.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The raw .mcp.json servers of trusted project directories that the user approved outside the repository. Claude ignores
 * .mcp.json of --add-dir directories, so a group session gets them through its own --mcp-config; an unapproved server
 * is never copied, and a broken directory is skipped instead of blocking the launch.
 */
@Component
public class ProjectMcpServers {

    private static final Logger log = LoggerFactory.getLogger(ProjectMcpServers.class);
    private static final String PROJECT_CONFIG_FILE = ".mcp.json";
    private static final String SERVERS = "mcpServers";
    private static final String COMMAND = "command";
    private static final String ARGS = "args";
    private static final List<String> RELATIVE_PREFIXES = List.of("./", "../", ".\\", "..\\");

    private final McpConfigReader reader;

    ProjectMcpServers(McpConfigReader reader) {
        this.reader = reader;
    }

    /** The first directory wins on a duplicate server name. */
    public Map<String, JsonNode> approved(List<Path> projectDirectories) {
        var merged = new LinkedHashMap<String, JsonNode>();
        for (var directory : projectDirectories) {
            try {
                approved(directory).forEach(merged::putIfAbsent);
            } catch (IOException | RuntimeException e) {
                // only the type: a parse message may quote the file content
                log.warn("MCP servers of {} skipped: {}", directory.getFileName(), e.getClass().getSimpleName());
            }
        }
        return merged;
    }

    private Map<String, JsonNode> approved(Path directory) throws IOException {
        var names = reader.approvedOutsideRepository(directory);
        var servers = new LinkedHashMap<String, JsonNode>();
        if (names.isEmpty()) {
            return servers;
        }
        var file = directory.resolve(PROJECT_CONFIG_FILE);
        var declared = Files.isRegularFile(file) ? Json.parse(Files.readString(file)).path(SERVERS) : Json.parse("{}");
        declared.properties().stream().filter(entry -> names.contains(entry.getKey()) && entry.getValue() instanceof ObjectNode)
                .forEach(entry -> servers.put(entry.getKey(), absolutized((ObjectNode) entry.getValue().deepCopy(), directory)));
        return servers;
    }

    /**
     * Claude starts the server in the group folder, so paths relative to the member would miss. A relative command and the
     * arguments starting with ./ or ../ become absolute; URLs, connection strings, bare words and paths that leave the member
     * stay as written, as do env values.
     */
    private static ObjectNode absolutized(ObjectNode server, Path directory) {
        var root = directory.toAbsolutePath().normalize();
        var command = server.path(COMMAND).asString("");
        if ((command.contains("/") || command.contains("\\")) && !command.startsWith("-")) {
            server.put(COMMAND, inside(command, root));
        }
        if (server.path(ARGS).isArray()) {
            var args = new ArrayList<String>();
            server.path(ARGS).forEach(node -> {
                var arg = node.asString("");
                args.add(RELATIVE_PREFIXES.stream().anyMatch(arg::startsWith) ? inside(arg, root) : arg);
            });
            var array = server.putArray(ARGS);
            args.forEach(array::add);
        }
        return server;
    }

    /** The absolute form of a relative [value] that stays inside [root]; anything else comes back unchanged. */
    private static String inside(String value, Path root) {
        try {
            var path = Path.of(value);
            var absolute = root.resolve(path).normalize();
            return path.isAbsolute() || !absolute.startsWith(root) ? value : absolute.toString();
        } catch (InvalidPathException e) {
            return value;
        }
    }
}
