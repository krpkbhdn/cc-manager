package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEventType;
import com.github.krpkbhdn.ccmanager.claude.mcp.AssistantMcpServer;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint;
import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes the per-session files for claude: --settings (hooks, a statusLine that reports rate limits) and --mcp-config (cc-manager's own MCP server). */
@Component
public class HookSettingsWriter {

    private static final String HOOKS_DIR = "hooks";
    private static final String FILE_SUFFIX = ".json";
    private static final String MCP_SUFFIX = ".mcp.json";
    private static final String LIST_RUN_CONFIGURATIONS_TOOL = "list_run_configurations";
    private static final String BEARER_FROM_ENV = "Bearer $" + HookServer.SESSION_TOKEN_ENV;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]+");
    private static final int HOOK_TIMEOUT_SECONDS = 5;
    /** These calls are held until the user answers; the hook timeout is HookProperties.decisionHold plus this margin. */
    private static final int DECISION_TIMEOUT_MARGIN_SECONDS = 10;
    private static final int CURL_MAX_SECONDS = 3;
    /** WorktreeCreate replaces git behaviour, MessageDisplay is a token firehose, the rest never fire in sessions. */
    private static final Set<HookEventType> NOT_SUBSCRIBED = EnumSet.of(HookEventType.SETUP, HookEventType.WORKTREE_CREATE,
            HookEventType.WORKTREE_REMOVE, HookEventType.MESSAGE_DISPLAY, HookEventType.UNKNOWN);

    private final HookServer server;
    private final Path hooksRoot;
    /** Every app process writes into its own subdirectory named by its pid, so instances never touch each other's files. */
    private final Path hooksDir;
    private final JsonMapper mapper;
    private final boolean windows;
    private final int decisionTimeoutSeconds;
    private final CurlSupport curl;

    @Autowired
    public HookSettingsWriter(HookServer server, AppProperties properties, JsonMapper mapper, HookProperties hookProperties, CurlSupport curl) {
        this(server, properties, mapper, hookProperties, curl, System.getProperty("os.name").toLowerCase().startsWith("windows"));
    }

    HookSettingsWriter(HookServer server, AppProperties properties, JsonMapper mapper, HookProperties hookProperties, CurlSupport curl, boolean windows) {
        this.curl = curl;
        this.server = server;
        this.decisionTimeoutSeconds = (int) hookProperties.decisionHold().toSeconds() + DECISION_TIMEOUT_MARGIN_SECONDS;
        this.hooksRoot = properties.dataDir().resolve(HOOKS_DIR);
        this.hooksDir = hooksRoot.resolve(String.valueOf(ProcessHandle.current().pid()));
        this.mapper = mapper;
        this.windows = windows;
    }

    /** Removes the files of app processes that no longer run (a live process's directory stays however quiet its sessions are), and loose files from before the per-process directories. */
    @PostConstruct
    void deleteStaleFiles() throws IOException {
        if (!Files.isDirectory(hooksRoot)) {
            return;
        }
        try (var entries = Files.newDirectoryStream(hooksRoot)) {
            for (var entry : entries) {
                if (!Files.isDirectory(entry)) {
                    Files.deleteIfExists(entry);
                } else if (!entry.equals(hooksDir) && !isLiveProcess(entry.getFileName().toString())) {
                    TreeDeleter.delete(entry);
                }
            }
        }
    }

    private static boolean isLiveProcess(String directoryName) {
        try {
            return ProcessHandle.of(Long.parseLong(directoryName)).isPresent();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public SessionFiles write(String ccmSessionId) {
        return write(ccmSessionId, Map.of());
    }

    /** [extraServers] join the MCP config; the app's own server name stays reserved. */
    public SessionFiles write(String ccmSessionId, Map<String, JsonNode> extraServers) {
        return write(ccmSessionId, mcpConfig(McpEndpoint.SERVER_NAME, server.mcpUrl(ccmSessionId), HookServer.SESSION_TOKEN_ENV, extraServers));
    }

    /**
     * The Assistant session gets only the app server in its mcp file; the project server and the project-session secret stay out of it.
     * The file holds only a reference to the token env variable of the Assistant process, so it contains no secret.
     * The token alone reaches only auto-approved tools, every other tool needs the user's Allow in the app.
     */
    public SessionFiles writeAssistant(String ccmSessionId) {
        return write(ccmSessionId, mcpConfig(AssistantMcpServer.SERVER_NAME, server.assistantMcpUrl(), HookServer.ASSISTANT_TOKEN_ENV, Map.of()));
    }

    /** Value of {@link HookServer#SESSION_TOKEN_ENV} for the process of every session whose files this writer wrote. */
    public String sessionToken(String ccmSessionId) {
        return server.sessionToken(ccmSessionId);
    }

    /** Value of {@link HookServer#ASSISTANT_TOKEN_ENV} for the Assistant process. */
    public String assistantToken() {
        return server.assistantMcpToken();
    }

    private SessionFiles write(String ccmSessionId, ObjectNode mcpConfig) {
        var file = fileOf(ccmSessionId);
        var mcpFile = mcpFileOf(ccmSessionId);
        try {
            restrictToOwner(Files.createDirectories(hooksDir), "rwx------");
            writeOwnerOnly(file, mapper.writeValueAsString(settings(ccmSessionId)));
            writeOwnerOnly(mcpFile, mapper.writeValueAsString(mcpConfig));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write hook settings " + file, e);
        }
        return new SessionFiles(file, mcpFile);
    }

    /** The files name the session and its MCP endpoint; the group and others must not read them. Created with the permissions where POSIX exists. */
    private static void writeOwnerOnly(Path file, String content) throws IOException {
        var posix = file.getFileSystem().supportedFileAttributeViews().contains("posix");
        var temp = posix
                ? Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String singleQuoted(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    private static void restrictToOwner(Path path, String permissions) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }
    }

    public void delete(String ccmSessionId) {
        try {
            Files.deleteIfExists(fileOf(ccmSessionId));
            Files.deleteIfExists(mcpFileOf(ccmSessionId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete hook settings of " + ccmSessionId, e);
        }
    }

    ObjectNode settings(String ccmSessionId) {
        var hooks = mapper.createObjectNode();
        Arrays.stream(HookEventType.values())
                .filter(type -> !NOT_SUBSCRIBED.contains(type))
                .filter(type -> type != HookEventType.SESSION_START || curlKeepsTokenOffArguments())
                .forEach(type -> hooks.putArray(type.wireName()).addObject().putArray("hooks").add(handler(type, ccmSessionId)));
        var settings = mapper.createObjectNode();
        settings.set("hooks", hooks);
        if (curlKeepsTokenOffArguments()) {
            settings.set("statusLine", statusLine(ccmSessionId));
        }
        var permissions = settings.putObject("permissions");
        permissions.putArray("allow")
                .add("mcp__" + McpEndpoint.SERVER_NAME + "__" + LIST_RUN_CONFIGURATIONS_TOOL);
        permissions.putArray("deny").add("Read(" + absoluteRule(hooksRoot) + "/**)");
        return settings;
    }

    /** Claude Code rules take an absolute path as {@code //path}; Windows drives are written Git Bash style ({@code //c/Users/...}). shortcut: Bash cat is not covered by Read rules. */
    private String absoluteRule(Path path) {
        var text = path.toAbsolutePath().toString().replace('\\', '/');
        if (windows && text.length() > 1 && text.charAt(1) == ':') {
            return "//" + Character.toLowerCase(text.charAt(0)) + text.substring(2);
        }
        return "/" + text;
    }

    /** Only the read-only list tool is pre-approved; set and delete change a command the user later runs, so Claude asks first. */
    private ObjectNode mcpConfig(String serverName, String url, String tokenEnv, Map<String, JsonNode> extraServers) {
        var config = mapper.createObjectNode();
        var servers = config.putObject("mcpServers");
        var own = servers.putObject(serverName).put("type", "http").put("url", url);
        own.putObject("headers").put("Authorization", "Bearer ${" + tokenEnv + "}");
        extraServers.forEach((name, definition) -> servers.putIfAbsent(name, definition.deepCopy()));
        return config;
    }

    private ObjectNode handler(HookEventType type, String ccmSessionId) {
        if (type == HookEventType.SESSION_START) {
            return curlHandler(ccmSessionId);
        }
        return httpHandler(server.hookUrl(ccmSessionId), type.awaitsDecision() ? decisionTimeoutSeconds : HOOK_TIMEOUT_SECONDS);
    }

    /** The bearer token comes from the claude process environment; an unlisted variable would resolve to empty. */
    private ObjectNode httpHandler(String url, int timeoutSeconds) {
        var handler = mapper.createObjectNode().put("type", "http").put("url", url).put("timeout", timeoutSeconds);
        handler.putObject("headers").put("Authorization", BEARER_FROM_ENV);
        handler.putArray("allowedEnvVars").add(HookServer.SESSION_TOKEN_ENV);
        return handler;
    }

    /** SessionStart does not accept http handlers; async so a stalled curl can never delay startup. */
    private ObjectNode curlHandler(String ccmSessionId) {
        return mapper.createObjectNode().put("type", "command").put("command", curlCommand(server.hookUrl(ccmSessionId)))
                .put("async", true).put("timeout", HOOK_TIMEOUT_SECONDS);
    }

    /**
     * The statusLine payload is the only carrier of rate_limits. The command is one shell string (statusLine has no args form)
     * and prints nothing, so the status line stays empty. shortcut: a user's own statusLine is replaced in our sessions, chain it when asked.
     */
    private ObjectNode statusLine(String ccmSessionId) {
        return mapper.createObjectNode().put("type", "command").put("command", curlCommand(server.statusLineUrl(ccmSessionId)));
    }

    /**
     * One shell string valid in sh, Git Bash and PowerShell. curl 8.3+ reads the token from its own environment, so the argument
     * holds only the variable name and an unset variable makes curl fail instead of sending an empty header. An older or unknown
     * curl gets no curl command at all (SessionStart hook and statusLine are omitted): the token would land in curl's argv, and stdin is taken by the payload.
     */
    private String curlCommand(String url) {
        var arguments = new ArrayList<>(List.of(curlExecutable(), "-s", "-m", String.valueOf(CURL_MAX_SECONDS), "-X", "POST",
                "-H", quoted("Content-Type: application/json"),
                "--variable", "%" + HookServer.SESSION_TOKEN_ENV, "--expand-header", quoted("Authorization: Bearer {{" + HookServer.SESSION_TOKEN_ENV + "}}")));
        arguments.addAll(List.of("--data-binary", quoted("@-"), quoted(url)));
        return String.join(" ", arguments);
    }

    private boolean curlKeepsTokenOffArguments() {
        return curl.expandsEnvironment(curlExecutable());
    }

    private String curlExecutable() {
        return windows ? "curl.exe" : "curl";
    }

    private String quoted(String arg) {
        return windows ? "\"" + arg + "\"" : singleQuoted(arg);
    }

    private Path mcpFileOf(String ccmSessionId) {
        return hooksDir.resolve(ccmSessionId + MCP_SUFFIX);
    }

    private Path fileOf(String ccmSessionId) {
        if (!SAFE_ID.matcher(ccmSessionId).matches()) {
            throw new IllegalArgumentException("Invalid session id");
        }
        return hooksDir.resolve(ccmSessionId + FILE_SUFFIX);
    }
}
