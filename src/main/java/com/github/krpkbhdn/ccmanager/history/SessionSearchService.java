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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Full-text search over the indexed Claude transcripts; a snippet marks matches with char(2) before and char(3) after. */
@Service
@EnableConfigurationProperties(SessionHistoryProperties.class)
public class SessionSearchService {

    public record SearchHit(String claudeSessionId, String cwd, String path, String role, Instant at, String entryUuid,
            long lineOffset, String snippet) {}

    private static final int SNIPPET_TOKENS = 16;
    private static final int LINE_END_SCAN_BYTES = 64 * 1024;

    private final JdbcClient jdbc;
    private final TranscriptIndexRepository repository;
    private final HostEnvironment host;
    private final SessionHistoryProperties properties;

    public SessionSearchService(JdbcClient jdbc, TranscriptIndexRepository repository, HostEnvironment host, SessionHistoryProperties properties) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.host = host;
        this.properties = properties;
    }

    public List<SearchHit> search(String query, Optional<String> cwdPrefix, Optional<Instant> from, Optional<Instant> to) {
        var match = matchExpression(query);
        if (match.isEmpty()) {
            return List.of();
        }
        var sql = new StringBuilder("""
                SELECT e.rowid AS id, e.file_id AS file_id, e.rank AS score, e.at AS at
                FROM transcript_entry_fts e JOIN transcript_file f ON f.id = e.file_id
                WHERE transcript_entry_fts MATCH ?""");
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
        sql.append(" ORDER BY e.rank, e.at DESC, e.rowid LIMIT ").append(properties.searchLimit());
        // snippet() runs only for the limited rows: the inner query ranks, the outer one decorates
        var outer = """
                WITH top AS (%s)
                SELECT f.claude_session_id, f.cwd, f.path, e.role, e.at, e.entry_uuid, e.line_offset,
                    snippet(transcript_entry_fts, 0, char(2), char(3), '...', %d)
                FROM top JOIN transcript_entry_fts e ON e.rowid = top.id JOIN transcript_file f ON f.id = top.file_id
                WHERE transcript_entry_fts MATCH ?
                ORDER BY top.score, top.at DESC, top.id""".formatted(sql, SNIPPET_TOKENS);
        params.add(match);
        return jdbc.sql(outer).params(params)
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

    /** End of the last finished line, so a line still being written is indexed once it is complete; empty when unreadable. */
    private static OptionalLong completeLinesEnd(String path) {
        try (var reader = new RandomAccessFile(path, "r")) {
            var chunk = new byte[LINE_END_SCAN_BYTES];
            long end = reader.length();
            while (end > 0) {
                int length = (int) Math.min(chunk.length, end);
                reader.seek(end - length);
                reader.readFully(chunk, 0, length);
                for (int i = length - 1; i >= 0; i--) {
                    if (chunk[i] == '\n') {
                        return OptionalLong.of(end - length + i + 1);
                    }
                }
                end -= length;
            }
            return OptionalLong.of(0);
        } catch (IOException e) {
            return OptionalLong.empty();
        }
    }
}
