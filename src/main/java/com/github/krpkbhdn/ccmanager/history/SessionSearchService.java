package com.github.krpkbhdn.ccmanager.history;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Full-text search over the indexed Claude transcripts; a snippet marks matches with char(2) before and char(3) after. */
@Service
public class SessionSearchService {

    public record SearchHit(String claudeSessionId, String cwd, String path, String role, Instant at, String entryUuid,
            long lineOffset, String snippet) {}

    private static final int LIMIT = 100;
    private static final int SNIPPET_TOKENS = 16;

    private final JdbcClient jdbc;
    private final TranscriptIndexRepository repository;
    private final HostEnvironment host;

    public SessionSearchService(JdbcClient jdbc, TranscriptIndexRepository repository, HostEnvironment host) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.host = host;
    }

    public List<SearchHit> search(String query, Optional<String> cwdPrefix, Optional<Instant> from, Optional<Instant> to) {
        var match = matchExpression(query);
        if (match.isEmpty()) {
            return List.of();
        }
        var sql = new StringBuilder("""
                SELECT f.claude_session_id, f.cwd, f.path, e.role, e.at, e.entry_uuid, e.line_offset,
                    snippet(transcript_entry_fts, 0, char(2), char(3), '...', %d)
                FROM transcript_entry_fts e JOIN transcript_file f ON f.id = e.file_id
                WHERE transcript_entry_fts MATCH ?""".formatted(SNIPPET_TOKENS));
        var params = new ArrayList<Object>(List.of(match));
        cwdPrefix.ifPresent(path -> {
            sql.append(" AND ").append(TranscriptIndexRepository.projectCondition("f.cwd", ignoresPathCase()));
            params.addAll(TranscriptIndexRepository.projectParams(path, path + File.separator));
        });
        from.ifPresent(instant -> {
            sql.append(" AND e.at >= ?");
            params.add(instant.toEpochMilli());
        });
        to.ifPresent(instant -> {
            sql.append(" AND e.at <= ?");
            params.add(instant.toEpochMilli());
        });
        sql.append(" ORDER BY rank, e.at DESC LIMIT ").append(LIMIT);
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, row) -> new SearchHit(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        Instant.ofEpochMilli(rs.getLong(5)), rs.getString(6), rs.getLong(7), rs.getString(8)))
                .list();
    }

    /** Forgets the indexed entries of the project and everything below it; lines written before now are not indexed again. */
    public void clear(String projectPath) {
        repository.evictProject(projectPath, ignoresPathCase(), SessionSearchService::completeLinesEnd);
    }

    private boolean ignoresPathCase() {
        return host.windows() || host.os() == HostEnvironment.OperatingSystem.MACOS;
    }

    /** @return how many entries the index holds */
    public long indexedEntryCount() {
        return repository.totalEntries();
    }

    private static String matchExpression(String query) {
        var tokens = Arrays.stream(query.split("\\s+"))
                .map(token -> token.replace("\"", ""))
                .filter(token -> token.codePoints().anyMatch(Character::isLetterOrDigit))
                .map(token -> "\"" + token + "\"")
                .toList();
        return tokens.isEmpty() ? "" : String.join(" ", tokens) + "*";
    }

    /** shortcut: a file that ends mid-line keeps its old offset, so that line may be indexed once it is finished. */
    private static OptionalLong completeLinesEnd(String path) {
        try (var reader = new RandomAccessFile(path, "r")) {
            long size = reader.length();
            if (size == 0) {
                return OptionalLong.of(0);
            }
            reader.seek(size - 1);
            return reader.read() == '\n' ? OptionalLong.of(size) : OptionalLong.empty();
        } catch (IOException e) {
            return OptionalLong.empty();
        }
    }
}
