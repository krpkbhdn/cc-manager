package com.github.krpkbhdn.ccmanager.extension.mcp;

import com.github.krpkbhdn.ccmanager.extension.ClaudeCli;
import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.ExtensionProperties;
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope;
import com.github.krpkbhdn.ccmanager.extension.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * MCP servers: configuration is read from Claude's files, changes go through {@code claude mcp}.
 * Server configs hold secrets, so nothing here logs them. Blocking; throws {@link ExtensionException}.
 */
@Service
public class McpService {

    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.: -]*");

    private final ClaudeCli cli;
    private final McpConfigReader configReader;
    private final ExtensionProperties properties;

    McpService(ClaudeCli cli, McpConfigReader configReader, ExtensionProperties properties) {
        this.cli = cli;
        this.configReader = configReader;
        this.properties = properties;
    }

    /** Servers declared in the user config and, with a project, in its local and shared config (no plugin servers). */
    public List<McpServerConfig> servers(Path projectDirectory) {
        return configReader.read(projectDirectory);
    }

    /** Slow: claude health-checks every server. Includes plugin servers, which have no config of their own. */
    public List<McpHealth> health(Path projectDirectory) {
        return McpListParser.parse(cli.executeChecked(projectDirectory, properties.mcpHealthTimeout(), List.of("mcp", "list")));
    }

    /** Raw {@code claude mcp get} text; may contain secrets, show it but do not log it. */
    public String details(String name, Path projectDirectory) {
        return cli.executeChecked(projectDirectory, properties.mcpHealthTimeout(), List.of("mcp", "get", requireValidName(name)));
    }

    /** @param json the server config object, e.g. {@code {"type":"http","url":"..."}} */
    public void add(String name, String json, ExtensionScope scope, Path projectDirectory) {
        requireValidName(name);
        if (!Json.parse(json).isObject()) {
            throw new ExtensionException("The server config must be a JSON object");
        }
        try {
            cli.executeChecked(scope.workingDirectory(projectDirectory), properties.queryTimeout(),
                    List.of("mcp", "add-json", "--scope", scope.cliValue(), name, json));
        } catch (ExtensionException e) {
            throw new ExtensionException(without(json, e.getMessage()), without(json, e.output()));
        }
    }

    /** The CLI may echo the config or single env and header values in an error; keep them out of messages. */
    private static String without(String json, String text) {
        var redacted = text.replace(json, "<config>");
        for (var secret : secretValues(json)) {
            redacted = redacted.replace(secret, "<secret>");
        }
        return redacted;
    }

    private static List<String> secretValues(String json) {
        var values = new ArrayList<String>();
        var root = Json.parse(json);
        for (var field : List.of("env", "headers")) {
            root.path(field).forEach(value -> values.add(value.asString("")));
        }
        return values.stream().filter(value -> !value.isBlank()).sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    public void remove(String name, ExtensionScope scope, Path projectDirectory) {
        cli.executeChecked(scope.workingDirectory(projectDirectory), properties.queryTimeout(),
                List.of("mcp", "remove", "--scope", scope.cliValue(), requireValidName(name)));
    }

    private static String requireValidName(String name) {
        if (!SERVER_NAME.matcher(name).matches()) {
            throw new ExtensionException("Invalid server name: use letters, digits, spaces and '-', '_', '.', ':'");
        }
        return name;
    }
}
