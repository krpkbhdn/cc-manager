package com.github.krpkbhdn.ccmanager.claude.session;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the tail of a Claude transcript JSONL into readable entries. The format is internal to Claude Code and
 * version-unstable, so unknown lines and blocks are skipped instead of failing. Only files under the Claude projects
 * directory are read, so a hook payload cannot point the app at an arbitrary file.
 */
@Component
@EnableConfigurationProperties(TranscriptProperties.class)
public class TranscriptService {

    private static final int TITLE_CACHE_SIZE = 64;

    private final JsonMapper mapper;
    private final TranscriptLineParser parser;
    private final Path projectsRoot;
    private final TranscriptProperties properties;
    private final Map<String, CachedTitle> titleCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedTitle> eldest) {
            return size() > TITLE_CACHE_SIZE;
        }
    });

    @Autowired
    public TranscriptService(JsonMapper mapper, HostEnvironment host, TranscriptProperties properties) {
        this(mapper, host.claudeConfigDirectory().resolve("projects"), properties);
    }

    public TranscriptService(JsonMapper mapper, Path projectsRoot, TranscriptProperties properties) {
        this.properties = properties;
        this.mapper = mapper;
        this.parser = new TranscriptLineParser(mapper);
        this.projectsRoot = projectsRoot;
    }

    /** Last entries, empty when the path is missing, outside the projects directory, or unreadable. */
    public List<TranscriptEntry> read(String transcriptPath) {
        var entries = new ArrayList<TranscriptEntry>();
        tail(transcriptPath).forEach(line -> parser.parse(line).ifPresent(parsed -> entries.addAll(parsed.entries())));
        return List.copyOf(entries.subList(Math.max(0, entries.size() - properties.maxEntries()), entries.size()));
    }

    /** Entries of the lines within about 512 KB either side of the line starting at [lineOffset] (bytes); {@code index} is the first entry of that line, -1 when none. */
    public Window readWindow(String transcriptPath, long lineOffset) {
        var entries = new ArrayList<TranscriptEntry>();
        var index = -1;
        try {
            var file = checkedFile(transcriptPath);
            if (file.isEmpty()) {
                return new Window(List.of(), -1);
            }
            try (var reader = new RandomAccessFile(file.get().toFile(), "r")) {
                var length = reader.length();
                var start = Math.max(0, lineOffset - properties.windowBytes());
                var end = Math.min(length, lineOffset + properties.windowBytes());
                var bytes = new byte[(int) (end - start)];
                reader.seek(start);
                reader.readFully(bytes);
                var from = 0;
                for (var at = 0; at <= bytes.length; at++) {
                    var lineEnd = at == bytes.length;
                    if (!lineEnd && bytes[at] != '\n') {
                        continue;
                    }
                    var complete = !lineEnd || end == length;
                    var skipped = from == 0 && start > 0;
                    if (complete && !skipped) {
                        var parsed = parser.parse(new String(bytes, from, at - from, StandardCharsets.UTF_8));
                        if (parsed.isPresent()) {
                            if (index < 0 && start + from >= lineOffset) {
                                index = entries.size();
                            }
                            entries.addAll(parsed.get().entries());
                        }
                    }
                    from = at + 1;
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Window(List.of(), -1);
        }
        return new Window(List.copyOf(entries), index);
    }

    public record Window(List<TranscriptEntry> entries, int index) {}

    /** Latest user-given title, else the latest generated one; empty when the transcript has none. Keeps the last title found when the tail window no longer holds one. */
    public Optional<String> title(String transcriptPath) {
        if (transcriptPath == null || transcriptPath.isBlank()) {
            return Optional.empty();
        }
        var stamp = stampOf(transcriptPath);
        if (stamp == null) {
            return readTitles(transcriptPath, properties.tailBytes()).best();
        }
        var cached = titleCache.get(transcriptPath);
        if (cached != null && cached.stamp().equals(stamp)) {
            return cached.titles().best();
        }
        var read = readTitles(transcriptPath, cached == null ? properties.tailBytes() : properties.refreshTailBytes());
        var titles = cached == null ? read : new Titles(read.custom().or(cached.titles()::custom), read.generated().or(cached.titles()::generated));
        titleCache.put(transcriptPath, new CachedTitle(stamp, titles));
        return titles.best();
    }

    private record FileStamp(long size, long modifiedMillis) {}

    private record CachedTitle(FileStamp stamp, Titles titles) {}

    /** The custom title (user /rename) wins over the generated one; each is kept separately so a short tail cannot replace one with the other. */
    private record Titles(Optional<String> custom, Optional<String> generated) {
        Optional<String> best() {
            return custom.or(() -> generated);
        }
    }

    private static FileStamp stampOf(String transcriptPath) {
        try {
            var file = Path.of(transcriptPath);
            return new FileStamp(Files.size(file), Files.getLastModifiedTime(file).toMillis());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private Titles readTitles(String transcriptPath, int tailBytes) {
        String custom = null;
        String generated = null;
        for (var line : tail(transcriptPath, tailBytes)) {
            if (!line.contains("-title\"")) {
                continue;
            }
            try {
                var node = mapper.readTree(line);
                switch (node.path("type").asString("")) {
                    case "custom-title" -> custom = nonBlank(node.path("customTitle").asString("")).orElse(custom);
                    case "ai-title" -> generated = nonBlank(node.path("aiTitle").asString("")).orElse(generated);
                    default -> { }
                }
            } catch (RuntimeException e) {
                continue;
            }
        }
        return new Titles(Optional.ofNullable(custom), Optional.ofNullable(generated));
    }

    private static Optional<String> nonBlank(String value) {
        return Optional.of(value.strip()).filter(text -> !text.isEmpty());
    }

    private List<String> tail(String transcriptPath) {
        return tail(transcriptPath, properties.tailBytes());
    }

    private List<String> tail(String transcriptPath, int tailBytes) {
        if (transcriptPath == null || transcriptPath.isBlank()) {
            return List.of();
        }
        try {
            var file = checkedFile(transcriptPath);
            return file.isPresent() ? tailLines(file.get(), tailBytes) : List.of();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private Optional<Path> checkedFile(String transcriptPath) throws IOException {
        if (transcriptPath == null || transcriptPath.isBlank()) {
            return Optional.empty();
        }
        var file = Path.of(transcriptPath).toRealPath();
        var inside = file.startsWith(projectsRoot.toRealPath()) && Files.isRegularFile(file);
        return inside ? Optional.of(file) : Optional.empty();
    }

    /** Complete lines of the last [tailBytes]: a cut first line and a still-being-written last line are dropped. */
    private static List<String> tailLines(Path file, int tailBytes) throws IOException {
        try (var reader = new RandomAccessFile(file.toFile(), "r")) {
            var length = reader.length();
            var start = Math.max(0, length - tailBytes);
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
}
