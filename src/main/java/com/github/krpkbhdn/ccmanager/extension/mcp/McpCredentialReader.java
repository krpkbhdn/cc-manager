package com.github.krpkbhdn.ccmanager.extension.mcp;

import static com.github.krpkbhdn.ccmanager.extension.Json.text;

import com.github.krpkbhdn.ccmanager.extension.ExtensionException;
import com.github.krpkbhdn.ccmanager.extension.Json;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

/**
 * Finds which MCP servers hold a stored OAuth token in {@code ~/.claude/.credentials.json}. The file also holds secrets:
 * only server names are returned, token values never leave this class, and the file is never written.
 * Without the file (macOS keeps tokens in the Keychain) no server is known to be logged in.
 */
@Component
class McpCredentialReader {

    private static final String CREDENTIALS_FILE = ".credentials.json";
    private static final String OAUTH = "mcpOAuth";

    private final HostEnvironment host;

    McpCredentialReader(HostEnvironment host) {
        this.host = host;
    }

    Set<String> loggedInServers() {
        var file = host.home().resolve(".claude").resolve(CREDENTIALS_FILE);
        if (!Files.isRegularFile(file)) {
            return Set.of();
        }
        try {
            var entries = Json.parse(Files.readString(file)).path(OAUTH);
            return StreamSupport.stream(entries.spliterator(), false)
                    .filter(entry -> !text(entry, "accessToken").isBlank())
                    .map(entry -> text(entry, "serverName"))
                    .filter(name -> !name.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException | ExtensionException e) {
            return Set.of();
        }
    }
}
