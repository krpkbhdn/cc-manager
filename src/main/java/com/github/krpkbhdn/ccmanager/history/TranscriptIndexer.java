package com.github.krpkbhdn.ccmanager.history;

import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry.Kind;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptLineParser;
import com.github.krpkbhdn.ccmanager.history.TranscriptIndexRepository.NewEntry;
import com.github.krpkbhdn.ccmanager.history.TranscriptIndexRepository.Progress;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the full-text index of Claude transcripts current: every scan reads each transcript from where the last scan
 * stopped, in batches of whole lines, each committed with its offset. Secrets are masked; tool calls on sensitive files
 * and their results are not indexed; transcripts that vanished or were rewritten are forgotten or read again.
 */
@Component
@EnableConfigurationProperties(SessionHistoryProperties.class)
public class TranscriptIndexer {

    private static final Logger log = LoggerFactory.getLogger(TranscriptIndexer.class);
    private static final String TRANSCRIPT_SUFFIX = ".jsonl";
    private static final String TOOL_ID_SEPARATOR = " ";
    private static final int TRANSCRIPT_DEPTH = 2;
    private static final long BATCH_BYTES = 8L << 20;
    private static final long MAX_LINE_BYTES = 1L << 30;
    private static final Pattern UUID_NAME = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    private record Line(String text, long byteLength) {}

    private record Pass(long fileId, Set<String> sensitiveToolIds, String sessionId, String cwd, long lastEntryAt, int added) {

        Pass seen(String lineSessionId, String lineCwd) {
            return new Pass(fileId, sensitiveToolIds, lineSessionId.isEmpty() ? sessionId : lineSessionId,
                    lineCwd.isEmpty() ? cwd : lineCwd, lastEntryAt, added);
        }

        Pass withSensitive(String toolUseId) {
            var ids = new LinkedHashSet<>(sensitiveToolIds);
            ids.add(toolUseId);
            return new Pass(fileId, ids, sessionId, cwd, lastEntryAt, added);
        }

        Pass withEntryAt(long at) {
            return new Pass(fileId, sensitiveToolIds, sessionId, cwd, Math.max(lastEntryAt, at), added + 1);
        }
    }

    private final Path projectsRoot;
    private final TranscriptIndexRepository repository;
    private final TranscriptLineParser parser;
    private final SessionHistoryProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofVirtual().name("transcript-indexer").unstarted(runnable));

    @Autowired
    public TranscriptIndexer(HostEnvironment host, JsonMapper mapper, TranscriptIndexRepository repository,
            SessionHistoryProperties properties) {
        this(host.claudeConfigDirectory().resolve("projects"), mapper, repository, properties);
    }

    public TranscriptIndexer(Path projectsRoot, JsonMapper mapper, TranscriptIndexRepository repository,
            SessionHistoryProperties properties) {
        this.projectsRoot = projectsRoot;
        this.parser = new TranscriptLineParser(mapper);
        this.repository = repository;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        var interval = properties.scanInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::scan, interval, interval, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() throws InterruptedException {
        scheduler.shutdownNow();
        scheduler.awaitTermination(SHUTDOWN_WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * One pass over all transcripts, then forgetting of vanished ones and eviction down to the entry limit; a failing
     * file is skipped. Nothing escapes, because a scheduled task that throws is never run again.
     */
    public void scan() {
        try {
            var files = transcriptFiles();
            files.forEach(this::indexQuietly);
            if (Files.isDirectory(projectsRoot)) {
                var paths = files.stream().map(Path::toString).collect(Collectors.toSet());
                repository.inWriteTransaction(() -> repository.removeFilesExcept(paths));
            }
            repository.inWriteTransaction(this::evictOverLimit);
        } catch (RuntimeException | Error e) {
            log.error("Transcript scan failed: {}", e.getClass().getSimpleName());
        }
    }

    private List<Path> transcriptFiles() {
        if (!Files.isDirectory(projectsRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(projectsRoot, TRANSCRIPT_DEPTH)) {
            return files.filter(path -> path.getFileName().toString().endsWith(TRANSCRIPT_SUFFIX))
                    .filter(path -> path.getNameCount() - projectsRoot.getNameCount() == TRANSCRIPT_DEPTH)
                    .filter(Files::isRegularFile)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void indexQuietly(Path file) {
        try {
            if (insideProjects(file)) {
                boolean more;
                do {
                    more = indexBatch(file);
                } while (more);
            }
        } catch (RuntimeException e) {
            log.warn("Could not index a transcript: {}", e.getClass().getSimpleName());
        }
    }

    private boolean insideProjects(Path file) {
        try {
            return file.toRealPath().startsWith(projectsRoot.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    /** Indexes the next batch of whole lines in one transaction; true when more of the file may be left to read. */
    private boolean indexBatch(Path file) {
        var path = file.toString();
        return repository.inWriteTransaction(() -> {
            var size = fileSize(file);
            var state = repository.findFile(path).orElseGet(() -> repository.createFile(path, stem(file)));
            if (rewritten(file, state.indexedBytes(), size)) {
                repository.resetFile(state.id());
                state = repository.findFile(path).orElseThrow();
            }
            var lines = state.indexedBytes() == size ? List.<Line>of() : nextLines(file, state.indexedBytes(), size);
            if (lines.isEmpty()) {
                return false;
            }
            var sensitive = state.sensitiveToolIds().isBlank() ? Set.<String>of()
                    : new LinkedHashSet<>(Arrays.asList(state.sensitiveToolIds().split(TOOL_ID_SEPARATOR)));
            var pass = new Pass(state.id(), sensitive, "", "", 0, 0);
            long offset = state.indexedBytes();
            for (var line : lines) {
                pass = indexLine(line.text(), offset, pass);
                offset += line.byteLength();
            }
            var sessionId = UUID_NAME.matcher(stem(file)).matches() ? "" : pass.sessionId();
            repository.recordProgress(state.id(), new Progress(offset, size, modifiedMillis(file), sessionId, pass.cwd(),
                    pass.lastEntryAt(), pass.added(), String.join(TOOL_ID_SEPARATOR, pass.sensitiveToolIds())));
            return offset < size;
        });
    }

    private Pass indexLine(String text, long lineOffset, Pass pass) {
        var parsed = parser.parse(text);
        if (parsed.isEmpty()) {
            return pass;
        }
        var line = parsed.get();
        var current = pass.seen(line.sessionId(), line.cwd());
        for (var entry : line.entries()) {
            if (entry.kind() == Kind.TOOL_CALL && SecretFilter.touchesSensitiveFile(entry.fullInput())) {
                current = current.withSensitive(entry.toolUseId());
                continue;
            }
            if (entry.kind() == Kind.TOOL_RESULT && current.sensitiveToolIds().contains(entry.toolUseId())) {
                continue;
            }
            var masked = SecretFilter.mask(entry.text());
            if (masked.isBlank()) {
                continue;
            }
            long at = line.at().toEpochMilli();
            repository.insertEntry(current.fileId(), new NewEntry(role(entry.kind()), masked, line.uuid(), lineOffset, at));
            current = current.withEntryAt(at);
        }
        return current;
    }

    private void evictOverLimit() {
        var total = repository.totalEntries();
        for (var fileId : repository.fileIdsOldestFirst()) {
            if (total <= properties.maxEntries()) {
                return;
            }
            total -= repository.entryCount(fileId);
            repository.evictFile(fileId);
        }
    }

    private static String role(Kind kind) {
        return switch (kind) {
            case PROMPT -> "user";
            case ASSISTANT -> "assistant";
            case TOOL_CALL, TOOL_RESULT -> "tool";
        };
    }

    /** A file that shrank, or whose last indexed byte is no line end any more, was replaced and must be read from the start. */
    private static boolean rewritten(Path file, long indexedBytes, long size) {
        if (size < indexedBytes) {
            return true;
        }
        if (indexedBytes == 0) {
            return false;
        }
        try (var reader = new RandomAccessFile(file.toFile(), "r")) {
            reader.seek(indexedBytes - 1);
            return reader.read() != '\n';
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Up to a batch of whole lines from {@code from}; a longer first line widens the read until it fits, and an
     * unfinished last line is left out.
     */
    private static List<Line> nextLines(Path file, long from, long size) {
        try (var reader = new RandomAccessFile(file.toFile(), "r")) {
            long length = Math.min(size - from, BATCH_BYTES);
            while (true) {
                var bytes = new byte[(int) length];
                reader.seek(from);
                reader.readFully(bytes);
                var lines = wholeLines(bytes);
                if (!lines.isEmpty() || from + length >= size) {
                    return lines;
                }
                if (length >= MAX_LINE_BYTES) {
                    throw new IllegalStateException("A transcript line is too long to index");
                }
                length = Math.min(size - from, Math.min(length * 2, MAX_LINE_BYTES));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Line> wholeLines(byte[] bytes) {
        var lines = new ArrayList<Line>();
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\n') {
                lines.add(new Line(new String(bytes, start, i - start, StandardCharsets.UTF_8), i + 1 - start));
                start = i + 1;
            }
        }
        return lines;
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long modifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stem(Path file) {
        var name = file.getFileName().toString();
        return name.substring(0, name.length() - TRANSCRIPT_SUFFIX.length());
    }
}
