package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry.Kind;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the tail of a Claude transcript JSONL into readable entries. The format is internal to Claude Code and
 * version-unstable, so unknown lines and blocks are skipped instead of failing. Only files under the Claude projects
 * directory are read, so a hook payload cannot point the app at an arbitrary file.
 */
@Component
public class TranscriptService {

    private static final int MAX_ENTRIES = 300;
    private static final int TAIL_BYTES = 1 << 20;
    private static final int TEXT_LIMIT = 6000;
    private static final int INPUT_LIMIT = 400;

    private final JsonMapper mapper;
    private final Path projectsRoot;

    @Autowired
    public TranscriptService(JsonMapper mapper) {
        this(mapper, Path.of(System.getProperty("user.home"), ".claude", "projects"));
    }

    public TranscriptService(JsonMapper mapper, Path projectsRoot) {
        this.mapper = mapper;
        this.projectsRoot = projectsRoot;
    }

    /** Last entries, empty when the path is missing, outside the projects directory, or unreadable. */
    public List<TranscriptEntry> read(String transcriptPath) {
        if (transcriptPath == null || transcriptPath.isBlank()) {
            return List.of();
        }
        try {
            var file = Path.of(transcriptPath).toRealPath();
            if (!file.startsWith(projectsRoot.toRealPath()) || !Files.isRegularFile(file)) {
                return List.of();
            }
            var entries = new ArrayList<TranscriptEntry>();
            tailLines(file).forEach(line -> entries.addAll(entriesOf(line)));
            return List.copyOf(entries.subList(Math.max(0, entries.size() - MAX_ENTRIES), entries.size()));
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Complete lines of the last [TAIL_BYTES]: a cut first line and a still-being-written last line are dropped. */
    private static List<String> tailLines(Path file) throws IOException {
        try (var reader = new RandomAccessFile(file.toFile(), "r")) {
            var length = reader.length();
            var start = Math.max(0, length - TAIL_BYTES);
            var bytes = new byte[(int) (length - start)];
            reader.seek(start);
            reader.readFully(bytes);
            var lines = new ArrayList<>(List.of(new String(bytes, StandardCharsets.UTF_8).split("\n", -1)));
            if (start > 0 && !lines.isEmpty()) {
                lines.removeFirst();
            }
            if (!lines.isEmpty()) {
                lines.removeLast();
            }
            return lines;
        }
    }

    private List<TranscriptEntry> entriesOf(String line) {
        try {
            var node = mapper.readTree(line);
            var content = node.path("message").path("content");
            return switch (node.path("type").asString("")) {
                case "user" -> blocks(content, Kind.PROMPT, "You");
                case "assistant" -> blocks(content, Kind.ASSISTANT, "Agent");
                default -> List.of();
            };
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<TranscriptEntry> blocks(JsonNode content, Kind textKind, String speaker) {
        if (content.isString()) {
            return List.of(new TranscriptEntry(textKind, speaker, cut(content.asString(), TEXT_LIMIT)));
        }
        var entries = new ArrayList<TranscriptEntry>();
        for (var block : content) {
            switch (block.path("type").asString("")) {
                case "text" -> entries.add(new TranscriptEntry(textKind, speaker, cut(block.path("text").asString(""), TEXT_LIMIT)));
                case "tool_use" -> entries.add(new TranscriptEntry(Kind.TOOL_CALL, block.path("name").asString("tool"),
                        cut(block.path("input").toString(), INPUT_LIMIT)));
                case "tool_result" -> entries.add(new TranscriptEntry(Kind.TOOL_RESULT, "Result", cut(resultText(block.path("content")), TEXT_LIMIT)));
                default -> { }
            }
        }
        return entries;
    }

    private static String resultText(JsonNode content) {
        if (content.isString()) {
            return content.asString();
        }
        var text = new StringBuilder();
        content.forEach(part -> text.append(part.path("text").asString("")));
        return text.toString();
    }

    private static String cut(String value, int limit) {
        var stripped = value.strip();
        return stripped.length() <= limit ? stripped : stripped.substring(0, limit) + "…";
    }
}
