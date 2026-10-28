package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.app.mcp.AssistantMcpEndpoint;
import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes the per-session files for claude: --settings (hooks, a statusLine that reports rate limits) and --mcp-config (cc-manager's own MCP server). */
@Component
public class HookSettingsWriter {

    private static final String HOOKS_DIR = "hooks";
    private static final String FILE_SUFFIX = ".json";
    private static final String MCP_SUFFIX = ".mcp.json";
    private static final String MCP_SERVER = "cc-manager";
    private static final String LIST_RUN_CONFIGURATIONS_TOOL = "list_run_configurations";
    private static final String HOOK_CURL = "hook";
    private static final String STATUS_LINE_CURL = "status";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]+");
    private static final int HOOK_TIMEOUT_SECONDS = 5;
    /** These calls are held until the user answers; HookProperties.decisionHold stays a bit below. */
    private static final int DECISION_TIMEOUT_SECONDS = 600;
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

    @Autowired
    public HookSettingsWriter(HookServer server, AppProperties properties, JsonMapper mapper) {
        this(server, properties, mapper, System.getProperty("os.name").toLowerCase().startsWith("windows"));
    }

    HookSettingsWriter(HookServer server, AppProperties properties, JsonMapper mapper, boolean windows) {
        this.server = server;
        this.hooksRoot = properties.dataDir().resolve(HOOKS_DIR);
        this.hooksDir = hooksRoot.resolve(String.valueOf(ProcessHandle.current().pid()));
        this.mapper = mapper;
        this.windows = windows;
    }

    /** Removes the files of app processes that no longer run, and loose files from before the per-process directories. */
    @PostConstruct
    void deleteStaleFiles() throws IOException {
        if (!Files.isDirectory(hooksRoot)) {
            return;
        }
        try (var entries = Files.newDirectoryStream(hooksRoot)) {
            for (var entry : entries) {
                if (!Files.isDirectory(entry)) {
                    Files.deleteIfExists(entry);
                } else if (!isLiveProcess(entry.getFileName().toString())) {
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
        return write(ccmSessionId, mcpConfig(MCP_SERVER, server.mcpUrl(ccmSessionId)));
    }

    /** The Assistant session gets only the app server in its mcp file; the project server and the project-session secret stay out of it. */
    public SessionFiles writeAssistant(String ccmSessionId) {
        return write(ccmSessionId, mcpConfig(AssistantMcpEndpoint.SERVER_NAME, server.assistantMcpUrl()));
    }

    private SessionFiles write(String ccmSessionId, ObjectNode mcpConfig) {
        var file = fileOf(ccmSessionId);
        var mcpFile = mcpFileOf(ccmSessionId);
        try {
            restrictToOwner(Files.createDirectories(hooksDir), "rwx------");
            if (!windows) {
                writeCurlConfig(ccmSessionId, HOOK_CURL, server.hookUrl(ccmSessionId));
                writeCurlConfig(ccmSessionId, STATUS_LINE_CURL, server.statusLineUrl(ccmSessionId));
            }
            Files.writeString(file, mapper.writeValueAsString(settings(ccmSessionId)));
            restrictToOwner(file, "rw-------");
            Files.writeString(mcpFile, mapper.writeValueAsString(mcpConfig));
            restrictToOwner(mcpFile, "rw-------");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write hook settings " + file, e);
        }
        return new SessionFiles(file, mcpFile);
    }

    /** curl reads the secret URL from this file, so it never shows in a process list. Not used on Windows, see curlArgs. */
    private void writeCurlConfig(String ccmSessionId, String kind, String url) throws IOException {
        var file = curlConfigOf(ccmSessionId, kind);
        Files.writeString(file, "url = \"" + url + "\"\n");
        restrictToOwner(file, "rw-------");
    }

    /** The hook secret sits in the URLs of these files; the group and others must not read it. No-op where there are no POSIX permissions. */
    private static void restrictToOwner(Path path, String permissions) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(permissions));
        }
    }

    public void delete(String ccmSessionId) {
        try {
            Files.deleteIfExists(fileOf(ccmSessionId));
            Files.deleteIfExists(mcpFileOf(ccmSessionId));
            Files.deleteIfExists(curlConfigOf(ccmSessionId, HOOK_CURL));
            Files.deleteIfExists(curlConfigOf(ccmSessionId, STATUS_LINE_CURL));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete hook settings of " + ccmSessionId, e);
        }
    }

    ObjectNode settings(String ccmSessionId) {
        var hooks = mapper.createObjectNode();
        Arrays.stream(HookEventType.values())
                .filter(type -> !NOT_SUBSCRIBED.contains(type))
                .forEach(type -> hooks.putArray(type.wireName()).addObject().putArray("hooks").add(handler(type, ccmSessionId)));
        var settings = mapper.createObjectNode();
        settings.set("hooks", hooks);
        settings.set("statusLine", statusLine(ccmSessionId));
        settings.putObject("permissions").putArray("allow").add("mcp__" + MCP_SERVER + "__" + LIST_RUN_CONFIGURATIONS_TOOL);
        return settings;
    }

    /** Only the read-only list tool is pre-approved; set and delete change a command the user later runs, so Claude asks first. */
    private ObjectNode mcpConfig(String serverName, String url) {
        var config = mapper.createObjectNode();
        config.putObject("mcpServers").putObject(serverName).put("type", "http").put("url", url);
        return config;
    }

    private ObjectNode handler(HookEventType type, String ccmSessionId) {
        if (type == HookEventType.SESSION_START) {
            return curlHandler(ccmSessionId);
        }
        return httpHandler(server.hookUrl(ccmSessionId), type.awaitsDecision() ? DECISION_TIMEOUT_SECONDS : HOOK_TIMEOUT_SECONDS);
    }

    private ObjectNode httpHandler(String url, int timeoutSeconds) {
        return mapper.createObjectNode().put("type", "http").put("url", url).put("timeout", timeoutSeconds);
    }

    /** SessionStart does not accept http handlers; async so a stalled curl can never delay startup. */
    private ObjectNode curlHandler(String ccmSessionId) {
        var handler = mapper.createObjectNode().put("type", "command").put("command", curlExecutable());
        curlArgs(HOOK_CURL, ccmSessionId, server.hookUrl(ccmSessionId)).forEach(handler.putArray("args")::add);
        return handler.put("async", true).put("timeout", HOOK_TIMEOUT_SECONDS);
    }

    /**
     * The statusLine payload is the only carrier of rate_limits. The command is one shell string (statusLine has no args form)
     * and prints nothing, so the status line stays empty. shortcut: a user's own statusLine is replaced in our sessions, chain it when asked.
     */
    private ObjectNode statusLine(String ccmSessionId) {
        var quoted = curlArgs(STATUS_LINE_CURL, ccmSessionId, server.statusLineUrl(ccmSessionId)).stream().map(arg -> "\"" + arg + "\"").toList();
        var command = curlExecutable() + " " + String.join(" ", quoted);
        return mapper.createObjectNode().put("type", "command").put("command", command);
    }

    /** On Windows the URL stays an argument: Git Bash's mingw curl cannot open a -K path with non-ANSI characters such as a Cyrillic user folder. */
    private List<String> curlArgs(String kind, String ccmSessionId, String url) {
        var common = List.of("-s", "-m", String.valueOf(CURL_MAX_SECONDS), "-X", "POST", "-H", "Content-Type: application/json", "--data-binary", "@-");
        var args = new ArrayList<String>();
        if (windows) {
            args.addAll(common);
            args.add(url);
        } else {
            args.addAll(List.of("-K", curlConfigOf(ccmSessionId, kind).toString()));
            args.addAll(common);
        }
        return args;
    }

    private String curlExecutable() {
        return windows ? "curl.exe" : "curl";
    }

    private Path curlConfigOf(String ccmSessionId, String kind) {
        return hooksDir.resolve(fileOf(ccmSessionId).getFileName() + "." + kind + ".curl");
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
