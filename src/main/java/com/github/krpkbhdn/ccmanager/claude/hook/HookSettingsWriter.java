package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes the per-session settings file passed as claude --settings FILE; hooks only, nothing else. */
@Component
public class HookSettingsWriter {

    private static final String HOOKS_DIR = "hooks";
    private static final String FILE_SUFFIX = ".json";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]+");
    private static final int HOOK_TIMEOUT_SECONDS = 5;
    private static final int CURL_MAX_SECONDS = 3;
    /** WorktreeCreate replaces git behaviour, MessageDisplay is a token firehose, the rest never fire in sessions. */
    private static final Set<HookEventType> NOT_SUBSCRIBED = EnumSet.of(HookEventType.SETUP, HookEventType.WORKTREE_CREATE,
            HookEventType.WORKTREE_REMOVE, HookEventType.MESSAGE_DISPLAY, HookEventType.UNKNOWN);

    private final HookServer server;
    private final Path hooksDir;
    private final JsonMapper mapper;

    public HookSettingsWriter(HookServer server, AppProperties properties, JsonMapper mapper) {
        this.server = server;
        this.hooksDir = properties.dataDir().resolve(HOOKS_DIR);
        this.mapper = mapper;
    }

    /** Settings files belong to processes of the previous app run, which are gone. */
    @PostConstruct
    void deleteStaleFiles() throws IOException {
        if (!Files.isDirectory(hooksDir)) {
            return;
        }
        try (var files = Files.newDirectoryStream(hooksDir, "*" + FILE_SUFFIX)) {
            for (var file : files) {
                Files.deleteIfExists(file);
            }
        }
    }

    public Path write(String ccmSessionId) {
        var file = fileOf(ccmSessionId);
        try {
            Files.createDirectories(hooksDir);
            Files.writeString(file, mapper.writeValueAsString(settings(ccmSessionId)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write hook settings " + file, e);
        }
        return file;
    }

    public void delete(String ccmSessionId) {
        try {
            Files.deleteIfExists(fileOf(ccmSessionId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete hook settings of " + ccmSessionId, e);
        }
    }

    ObjectNode settings(String ccmSessionId) {
        var url = server.hookUrl(ccmSessionId);
        var hooks = mapper.createObjectNode();
        Arrays.stream(HookEventType.values())
                .filter(type -> !NOT_SUBSCRIBED.contains(type))
                .forEach(type -> hooks.putArray(type.wireName()).addObject().putArray("hooks").add(handler(type, url)));
        return mapper.createObjectNode().set("hooks", hooks);
    }

    private ObjectNode handler(HookEventType type, String url) {
        return type == HookEventType.SESSION_START ? curlHandler(url) : httpHandler(url);
    }

    private ObjectNode httpHandler(String url) {
        return mapper.createObjectNode().put("type", "http").put("url", url).put("timeout", HOOK_TIMEOUT_SECONDS);
    }

    /** SessionStart does not accept http handlers; async so a stalled curl can never delay startup. */
    private ObjectNode curlHandler(String url) {
        var handler = mapper.createObjectNode().put("type", "command").put("command", curlExecutable());
        var args = handler.putArray("args");
        List.of("-s", "-m", String.valueOf(CURL_MAX_SECONDS), "-X", "POST", "-H", "Content-Type: application/json",
                "--data-binary", "@-", url).forEach(args::add);
        return handler.put("async", true).put("timeout", HOOK_TIMEOUT_SECONDS);
    }

    private static String curlExecutable() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows") ? "curl.exe" : "curl";
    }

    private Path fileOf(String ccmSessionId) {
        if (!SAFE_ID.matcher(ccmSessionId).matches()) {
            throw new IllegalArgumentException("Invalid session id");
        }
        return hooksDir.resolve(ccmSessionId + FILE_SUFFIX);
    }
}
