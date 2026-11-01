package com.github.krpkbhdn.ccmanager.history;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** SQL of the transcript index: indexed files with their read offsets and the full-text entry rows. */
@Repository
public class TranscriptIndexRepository {

    /** Indexing state of one transcript file; {@code sensitiveToolIds} is space separated. */
    public record IndexedFile(long id, long indexedBytes, String sensitiveToolIds) {}

    public record NewEntry(String role, String text, String entryUuid, long lineOffset, long atMillis) {}

    /** What one indexing pass of a file learned; empty session id and cwd keep the stored values. */
    public record Progress(long indexedBytes, long sizeBytes, long modifiedAt, String sessionId, String cwd, long lastEntryAt,
            int addedEntries, String sensitiveToolIds) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public TranscriptIndexRepository(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /** Runs [work] in a transaction that takes the write lock first, so it never fails on a lock upgrade. */
    public <T> T inWriteTransaction(Supplier<T> work) {
        return transaction.execute(status -> {
            jdbc.sql("UPDATE transcript_file SET id = id WHERE 0").update();
            return work.get();
        });
    }

    public void inWriteTransaction(Runnable work) {
        inWriteTransaction(() -> {
            work.run();
            return null;
        });
    }

    public Optional<IndexedFile> findFile(String path) {
        return jdbc.sql("SELECT id, indexed_bytes, sensitive_tool_ids FROM transcript_file WHERE path = ?")
                .param(path)
                .query((rs, row) -> new IndexedFile(rs.getLong(1), rs.getLong(2), rs.getString(3)))
                .optional();
    }

    public IndexedFile createFile(String path, String claudeSessionId) {
        jdbc.sql("INSERT INTO transcript_file (path, claude_session_id) VALUES (?, ?)").params(path, claudeSessionId).update();
        return findFile(path).orElseThrow();
    }

    public void insertEntry(long fileId, NewEntry entry) {
        jdbc.sql("INSERT INTO transcript_entry_fts (text, role, file_id, entry_uuid, line_offset, at) VALUES (?, ?, ?, ?, ?, ?)")
                .params(entry.text(), entry.role(), fileId, entry.entryUuid(), entry.lineOffset(), entry.atMillis())
                .update();
        jdbc.sql("INSERT INTO transcript_entry_ref (entry_id, file_id) VALUES (last_insert_rowid(), ?)").param(fileId).update();
    }

    public void recordProgress(long fileId, Progress progress) {
        jdbc.sql("""
                UPDATE transcript_file SET indexed_bytes = ?, size_bytes = ?, modified_at = ?,
                    claude_session_id = COALESCE(NULLIF(?, ''), claude_session_id), cwd = COALESCE(NULLIF(?, ''), cwd),
                    last_entry_at = MAX(last_entry_at, ?), entry_count = entry_count + ?, sensitive_tool_ids = ?
                WHERE id = ?""")
                .params(progress.indexedBytes(), progress.sizeBytes(), progress.modifiedAt(), progress.sessionId(), progress.cwd(),
                        progress.lastEntryAt(), progress.addedEntries(), progress.sensitiveToolIds(), fileId)
                .update();
    }

    /** Drops the entries of a file that was rewritten, so it is read again from the start. */
    public void resetFile(long fileId) {
        deleteEntries(fileId);
        jdbc.sql("UPDATE transcript_file SET indexed_bytes = 0, entry_count = 0, sensitive_tool_ids = '' WHERE id = ?")
                .param(fileId).update();
    }

    public long totalEntries() {
        return jdbc.sql("SELECT COALESCE(SUM(entry_count), 0) FROM transcript_file").query(Long.class).single();
    }

    /** Files that hold entries, oldest last entry first. */
    public List<Long> fileIdsOldestFirst() {
        return jdbc.sql("SELECT id FROM transcript_file WHERE entry_count > 0 ORDER BY last_entry_at, id").query(Long.class).list();
    }

    public long entryCount(long fileId) {
        return jdbc.sql("SELECT entry_count FROM transcript_file WHERE id = ?").param(fileId).query(Long.class).single();
    }

    /** Forgets the entries of a file but keeps its offset, so the file is not indexed again. */
    public void evictFile(long fileId) {
        deleteEntries(fileId);
        jdbc.sql("UPDATE transcript_file SET entry_count = 0 WHERE id = ?").param(fileId).update();
    }

    /** Evicts every file whose cwd is the project path or matches {@code likePattern} (escape character {@code !}), ignoring case. */
    public void evictProject(String path, String likePattern) {
        inWriteTransaction(() -> jdbc.sql("SELECT id FROM transcript_file WHERE cwd = ? COLLATE NOCASE OR cwd LIKE ? ESCAPE '!'")
                .params(path, likePattern).query(Long.class).list().forEach(this::evictFile));
    }

    /** Forgets every file whose transcript is not in {@code existingPaths} any more, with its entries. */
    public void removeFilesExcept(Set<String> existingPaths) {
        var gone = jdbc.sql("SELECT id, path FROM transcript_file").query((rs, row) -> Map.entry(rs.getLong(1), rs.getString(2)))
                .list().stream().filter(file -> !existingPaths.contains(file.getValue())).map(Map.Entry::getKey).toList();
        for (var fileId : gone) {
            deleteEntries(fileId);
            jdbc.sql("DELETE FROM transcript_file WHERE id = ?").param(fileId).update();
        }
    }

    private void deleteEntries(long fileId) {
        jdbc.sql("DELETE FROM transcript_entry_fts WHERE rowid IN (SELECT entry_id FROM transcript_entry_ref WHERE file_id = ?)")
                .param(fileId).update();
        jdbc.sql("DELETE FROM transcript_entry_ref WHERE file_id = ?").param(fileId).update();
    }
}
