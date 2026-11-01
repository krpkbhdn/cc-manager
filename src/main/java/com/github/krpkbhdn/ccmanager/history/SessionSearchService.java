package com.github.krpkbhdn.ccmanager.history;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Full-text search over the indexed Claude transcripts; a snippet marks matches with char(2) before and char(3) after. */
@Service
public class SessionSearchService {

    public record SearchHit(String claudeSessionId, String cwd, String path, String role, Instant at, String entryUuid,
            long lineOffset, String snippet) {}

    private static final int LIMIT = 100;
    private static final int SNIPPET_TOKENS = 16;
    private static final String LIKE_ESCAPE = "!";

    private final JdbcClient jdbc;
    private final TranscriptIndexRepository repository;

    public SessionSearchService(JdbcClient jdbc, TranscriptIndexRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
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
            sql.append(" AND (f.cwd = ? COLLATE NOCASE OR f.cwd LIKE ? ESCAPE '").append(LIKE_ESCAPE).append("')");
            params.add(path);
            params.add(likePattern(path));
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

    /** Forgets the indexed entries of the project and everything below it; the files are not indexed again. */
    public void clear(String projectPath) {
        repository.evictProject(projectPath, likePattern(projectPath));
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

    private static String likePattern(String path) {
        return escapeLike(path + File.separator) + "%";
    }

    private static String escapeLike(String value) {
        return value.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE).replace("%", LIKE_ESCAPE + "%").replace("_", LIKE_ESCAPE + "_");
    }
}
