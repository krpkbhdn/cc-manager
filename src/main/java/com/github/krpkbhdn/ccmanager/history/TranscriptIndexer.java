package com.github.krpkbhdn.ccmanager.history;

import com.github.krpkbhdn.ccmanager.claude.session.TranscriptEntry.Kind;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptLineParser;
import com.github.krpkbhdn.ccmanager.history.TranscriptIndexRepository.IndexedFile;
import com.github.krpkbhdn.ccmanager.security.SecretFilter;
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
import java.util.Comparator;
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
    private static final String ALL_RESULTS_SENSITIVE = "*";
    private static final int TRANSCRIPT_DEPTH = 2;
    static final long MAX_LINE_BYTES = 16L << 20;
    private static final int SKIP_CHUNK_BYTES = 64 * 1024;
    private static final Pattern UUID_NAME = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    private record Line(String text, long byteLength) {}

    private record Pass(Set<String> sensitiveToolIds, String sessionId, String cwd, long lastEntryAt) {

        Pass seen(String lineSessionId, String lineCwd) {
            return new Pass(sensitiveToolIds, lineSessionId.isEmpty() ? sessionId : lineSessionId,
                    lineCwd.isEmpty() ? cwd : lineCwd, lastEntryAt);
        }

        Pass withSensitive(String toolUseId) {
            sensitiveToolIds.add(toolUseId);
            return this;
        }

        Pass withEntryAt(long at) {
            return new Pass(sensitiveToolIds, sessionId, cwd, Math.max(lastEntryAt, at));
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
            var root = realRoot();
            files.forEach(file -> indexQuietly(file, root));
            if (Files.isDirectory(projectsRoot)) {
                var paths = files.stream().map(Path::toString).collect(Collectors.toSet());
                if (repository.anyFileExcept(paths)) {
                    repository.inWriteTransaction(() -> repository.removeFilesExcept(paths));
                }
            }
            evictOverLimit();
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
                    .map(path -> new StampedFile(path, modifiedMillisOrZero(path)))
                    .sorted(Comparator.comparingLong(StampedFile::modifiedMillis).reversed())
                    .map(StampedFile::path)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void indexQuietly(Path file, Path root) {
        try {
            if (insideProjects(file, root)) {
                boolean more;
                do {
                    more = indexBatch(file);
                } while (more);
            }
        } catch (RuntimeException e) {
            log.warn("Could not index a transcript: {}", e.getClass().getSimpleName());
        }
    }

    private Path realRoot() {
        try {
            return projectsRoot.toRealPath();
        } catch (IOException e) {
            return projectsRoot;
        }
    }

    private static boolean insideProjects(Path file, Path realRoot) {
        try {
            return file.toRealPath().startsWith(realRoot);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Indexes the next batch of whole lines; true when more of the file may be left to read. Reading, parsing and masking
     * happen before the write transaction, which only inserts, so the write lock is held briefly.
     */
    private boolean indexBatch(Path file) {
        var path = file.toString();
        var size = fileSize(file);
        var known = repository.findFile(path);
        boolean reset = known.isPresent() && rewritten(file, known.get(), size);
        if (known.isPresent() && !reset && known.get().indexedBytes() == size) {
            return false;
        }
        long start = known.isEmpty() || reset ? 0 : known.get().indexedBytes();
        var lines = nextLines(file, start, size);
        if (lines.isEmpty() && !reset) {
            return false;
        }
        var sensitiveIds = reset ? "" : known.map(IndexedFile::sensitiveToolIds).orElse("");
        var sensitive = sensitiveIds.isBlank() ? new LinkedHashSet<String>()
                : new LinkedHashSet<>(Arrays.asList(sensitiveIds.split(TOOL_ID_SEPARATOR)));
        var entries = new ArrayList<NewEntry>();
        var pass = new Pass(sensitive, "", "", 0);
        long offset = start;
        for (var line : lines) {
            pass = indexLine(line.text(), offset, pass, entries);
            offset += line.byteLength();
        }
        // fails closed: past the cap the ids are forgotten, so every later tool result of the file is skipped
        var ids = pass.sensitiveToolIds();
        if (ids.size() > properties.maxSensitiveToolIds()) {
            ids.clear();
            ids.add(ALL_RESULTS_SENSITIVE);
        }
        var sessionId = UUID_NAME.matcher(stem(file)).matches() ? "" : pass.sessionId();
        var finalOffset = offset;
        var finalPass = pass;
        return repository.inWriteTransaction(() -> {
            var current = repository.findFile(path);
            if (!current.map(IndexedFile::indexedBytes).equals(known.map(IndexedFile::indexedBytes))) {
                return true;
            }
            var fileId = current.map(IndexedFile::id).orElseGet(() -> repository.createFile(path, stem(file)).id());
            if (reset) {
                repository.resetFile(fileId);
            }
            entries.forEach(entry -> repository.insertEntry(fileId, entry));
            repository.recordProgress(fileId, new Progress(finalOffset, size, modifiedMillis(file), sessionId, finalPass.cwd(),
                    finalPass.lastEntryAt(), entries.size(), String.join(TOOL_ID_SEPARATOR, finalPass.sensitiveToolIds())));
            return finalOffset < size;
        });
    }

    private Pass indexLine(String text, long lineOffset, Pass pass, List<NewEntry> sink) {
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
            if (entry.kind() == Kind.TOOL_RESULT && (current.sensitiveToolIds().contains(entry.toolUseId())
                    || current.sensitiveToolIds().contains(ALL_RESULTS_SENSITIVE))) {
                continue;
            }
            var masked = SecretFilter.mask(entry.text());
            if (masked.isBlank()) {
                continue;
            }
            long at = line.at().toEpochMilli();
            sink.add(new NewEntry(role(entry.kind()), masked, line.uuid(), lineOffset, at));
            current = current.withEntryAt(at);
        }
        return current;
    }

    /**
     * Trims the oldest files' oldest entries down to the limit, so one huge growing file loses only its excess. Each
     * chunk is its own write transaction, so other writers are never blocked for long.
     */
    private void evictOverLimit() {
        var excess = repository.totalEntries() - properties.maxEntries();
        while (excess > 0) {
            var chunk = Math.min(excess, properties.evictChunk());
            var evicted = repository.inWriteTransaction(() -> evictOldest(chunk));
            if (evicted == 0) {
                return;
            }
            excess -= evicted;
        }
    }

    private long evictOldest(long wanted) {
        var left = wanted;
        for (var file : repository.filesWithEntriesOldestFirst()) {
            var count = Math.min(left, file.entryCount());
            repository.evictOldestEntries(file.id(), count);
            left -= count;
            if (left <= 0) {
                break;
            }
        }
        return wanted - left;
    }

    private static String role(Kind kind) {
        return switch (kind) {
            case PROMPT -> "user";
            case ASSISTANT -> "assistant";
            case TOOL_CALL, TOOL_RESULT -> "tool";
        };
    }

    /**
     * A file that shrank, whose last indexed byte is no line end any more, or that is fully indexed yet has a new
     * modification time (replaced by a same-size file), was replaced and must be read from the start.
     */
    private static boolean rewritten(Path file, IndexedFile known, long size) {
        long indexedBytes = known.indexedBytes();
        if (size < indexedBytes) {
            return true;
        }
        if (indexedBytes == 0) {
            return false;
        }
        if (indexedBytes == size && known.modifiedAt() != modifiedMillis(file)) {
            return true;
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
    private List<Line> nextLines(Path file, long from, long size) {
        try (var reader = new RandomAccessFile(file.toFile(), "r")) {
            long length = Math.min(size - from, properties.batchBytes());
            while (true) {
                var bytes = new byte[(int) length];
                reader.seek(from);
                reader.readFully(bytes);
                var lines = wholeLines(bytes, properties.batchLines());
                if (!lines.isEmpty() || from + length >= size) {
                    return lines;
                }
                if (length >= MAX_LINE_BYTES) {
                    return skipOversizedLine(file, reader, from, size);
                }
                length = Math.min(size - from, Math.min(length * 2, MAX_LINE_BYTES));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Steps over a line too long to index as an empty line of its byte length, or reads nothing while it is unfinished. */
    private static List<Line> skipOversizedLine(Path file, RandomAccessFile reader, long from, long size) throws IOException {
        var chunk = new byte[SKIP_CHUNK_BYTES];
        long position = from;
        reader.seek(position);
        while (position < size) {
            int read = reader.read(chunk, 0, (int) Math.min(chunk.length, size - position));
            if (read < 0) {
                break;
            }
            for (int i = 0; i < read; i++) {
                if (chunk[i] == '\n') {
                    long lineBytes = position + i + 1 - from;
                    log.warn("Skipped an oversized transcript line of {} bytes in {}", lineBytes, file.getFileName());
                    return List.of(new Line("", lineBytes));
                }
            }
            position += read;
        }
        return List.of();
    }

    private static List<Line> wholeLines(byte[] bytes, int maxLines) {
        var lines = new ArrayList<Line>();
        int start = 0;
        for (int i = 0; i < bytes.length && lines.size() < maxLines; i++) {
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

    /** The modification time read once, so a file being written cannot change its place while sorting. */
    private record StampedFile(Path path, long modifiedMillis) {
    }

    private static long modifiedMillisOrZero(Path file) {
        try {
            return modifiedMillis(file);
        } catch (UncheckedIOException e) {
            return 0;
        }
    }

    private static String stem(Path file) {
        var name = file.getFileName().toString();
        return name.substring(0, name.length() - TRANSCRIPT_SUFFIX.length());
    }
}
