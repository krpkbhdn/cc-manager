package com.github.krpkbhdn.ccmanager.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.history.TranscriptIndexRepository.NewEntry;
import com.github.krpkbhdn.ccmanager.history.TranscriptIndexRepository.Progress;
import java.io.File;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranscriptIndexRepositoryTest {

    @TempDir
    Path temp;

    HistoryFixture fixture;
    TranscriptIndexRepository repository;

    @BeforeEach
    void setUp() {
        fixture = new HistoryFixture(temp);
        repository = fixture.repository;
    }

    private long ftsRows() {
        return fixture.jdbc.sql("SELECT COUNT(*) FROM transcript_entry_fts").query(Long.class).single();
    }

    private long refRows() {
        return fixture.jdbc.sql("SELECT COUNT(*) FROM transcript_entry_ref").query(Long.class).single();
    }

    private void addEntries(long fileId, int count) {
        repository.inWriteTransaction(() -> {
            for (int i = 0; i < count; i++) {
                repository.insertEntry(fileId, new NewEntry("user", "text " + i, "u" + i, i, i));
            }
        });
    }

    private Progress progress(long bytes, String sessionId, String cwd, long lastEntryAt, int added) {
        return new Progress(bytes, bytes, 5, sessionId, cwd, lastEntryAt, added, "");
    }

    @Test
    void createdFileIsFoundByPathWithZeroOffset() {
        var created = repository.createFile("/t/a.jsonl", "s1");

        assertThat(repository.findFile("/t/a.jsonl")).hasValue(created);
        assertThat(created.indexedBytes()).isZero();
        assertThat(repository.findFile("/t/other.jsonl")).isEmpty();
    }

    @Test
    void creatingTheSamePathTwiceViolatesUniqueness() {
        repository.createFile("/t/a.jsonl", "s1");

        assertThatThrownBy(() -> repository.createFile("/t/a.jsonl", "s1")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void insertEntryAddsAFullTextRowAndARef() {
        var file = repository.createFile("/t/a.jsonl", "s1");

        addEntries(file.id(), 2);

        assertThat(ftsRows()).isEqualTo(2);
        assertThat(refRows()).isEqualTo(2);
    }

    @Test
    void recordProgressStoresOffsetAndKeepsStoredSessionAndCwdWhenGivenEmpty() {
        var file = repository.createFile("/t/a.jsonl", "s1");
        repository.recordProgress(file.id(), progress(100, "s2", "/w/app", 50, 3));

        repository.recordProgress(file.id(), progress(200, "", "", 10, 2));

        assertThat(repository.findFile("/t/a.jsonl").orElseThrow().indexedBytes()).isEqualTo(200);
        assertThat(repository.totalEntries()).isEqualTo(5);
        var row = fixture.jdbc.sql("SELECT claude_session_id, cwd, last_entry_at FROM transcript_file")
                .query((rs, i) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getLong(3)).single();
        assertThat(row).isEqualTo("s2|/w/app|50");
    }

    @Test
    void totalEntriesIsZeroWithoutFiles() {
        assertThat(repository.totalEntries()).isZero();
    }

    @Test
    void resetFileDropsEntriesAndOffset() {
        var file = repository.createFile("/t/a.jsonl", "s1");
        var other = repository.createFile("/t/b.jsonl", "s2");
        addEntries(file.id(), 2);
        addEntries(other.id(), 1);
        repository.recordProgress(file.id(), progress(100, "", "", 1, 2));

        repository.resetFile(file.id());

        assertThat(repository.findFile("/t/a.jsonl").orElseThrow().indexedBytes()).isZero();
        assertThat(ftsRows()).isEqualTo(1);
        assertThat(refRows()).isEqualTo(1);
        assertThat(repository.totalEntries()).isZero();
    }

    @Test
    void filesWithEntriesAreListedOldestLastEntryFirstAndEmptyOnesSkipped() {
        var newer = repository.createFile("/t/new.jsonl", "s1");
        var older = repository.createFile("/t/old.jsonl", "s2");
        var empty = repository.createFile("/t/empty.jsonl", "s3");
        repository.recordProgress(newer.id(), progress(1, "", "", 900, 1));
        repository.recordProgress(older.id(), progress(1, "", "", 100, 4));

        assertThat(repository.filesWithEntriesOldestFirst()).extracting(TranscriptIndexRepository.EntryFile::id)
                .containsExactly(older.id(), newer.id()).doesNotContain(empty.id());
    }

    @Test
    void evictOldestEntriesForgetsOnlyTheOldestAndKeepsTheOffset() {
        var file = repository.createFile("/t/a.jsonl", "s1");
        addEntries(file.id(), 3);
        repository.recordProgress(file.id(), progress(77, "", "", 1, 3));

        repository.evictOldestEntries(file.id(), 2);

        assertThat(ftsRows()).isEqualTo(1);
        assertThat(refRows()).isEqualTo(1);
        assertThat(repository.totalEntries()).isEqualTo(1);
        assertThat(repository.findFile("/t/a.jsonl").orElseThrow().indexedBytes()).isEqualTo(77);
        assertThat(fixture.jdbc.sql("SELECT text FROM transcript_entry_fts").query(String.class).single()).isEqualTo("text 2");
    }

    @Test
    void evictProjectClearsFilesAtOrBelowThePathAndMovesTheirOffsets() {
        var inside = repository.createFile("/t/in.jsonl", "s1");
        var nested = repository.createFile("/t/nested.jsonl", "s2");
        var sibling = repository.createFile("/t/sibling.jsonl", "s3");
        var root = File.separator + "w" + File.separator + "app";
        repository.recordProgress(inside.id(), progress(500, "", root, 1, 1));
        repository.recordProgress(nested.id(), progress(500, "", root + File.separator + "sub", 1, 1));
        repository.recordProgress(sibling.id(), progress(500, "", root + "-other", 1, 1));
        addEntries(inside.id(), 1);
        addEntries(nested.id(), 1);
        addEntries(sibling.id(), 1);

        repository.evictProject(root, false, path -> path.endsWith("in.jsonl") ? OptionalLong.of(321) : OptionalLong.empty());

        assertThat(repository.findFile("/t/in.jsonl").orElseThrow().indexedBytes()).isEqualTo(321);
        assertThat(repository.findFile("/t/nested.jsonl").orElseThrow().indexedBytes()).isEqualTo(500);
        assertThat(repository.findFile("/t/sibling.jsonl").orElseThrow().indexedBytes()).isEqualTo(500);
        assertThat(repository.totalEntries()).isEqualTo(1);
        assertThat(ftsRows()).isEqualTo(1);
        assertThat(refRows()).isEqualTo(1);
    }

    @Test
    void evictProjectMatchingCanIgnoreCase() {
        var file = repository.createFile("/t/a.jsonl", "s1");
        repository.recordProgress(file.id(), progress(10, "", "C:\\Work\\App", 1, 1));
        addEntries(file.id(), 1);

        repository.evictProject("c:\\work\\app", false, path -> OptionalLong.empty());
        assertThat(repository.totalEntries()).isEqualTo(1);

        repository.evictProject("c:\\work\\app", true, path -> OptionalLong.empty());
        assertThat(repository.totalEntries()).isZero();
        assertThat(ftsRows()).isZero();
    }

    @Test
    void removeFilesExceptForgetsMissingFilesWithTheirEntries() {
        var keep = repository.createFile("/t/keep.jsonl", "s1");
        var gone = repository.createFile("/t/gone.jsonl", "s2");
        addEntries(keep.id(), 1);
        addEntries(gone.id(), 2);

        repository.removeFilesExcept(Set.of("/t/keep.jsonl"));

        assertThat(repository.findFile("/t/gone.jsonl")).isEmpty();
        assertThat(repository.findFile("/t/keep.jsonl")).isPresent();
        assertThat(ftsRows()).isEqualTo(1);
        assertThat(refRows()).isEqualTo(1);
    }

    @Test
    void removeFilesExceptWithEmptySetClearsEverything() {
        addEntries(repository.createFile("/t/a.jsonl", "s1").id(), 1);

        repository.removeFilesExcept(Set.of());

        assertThat(repository.findFile("/t/a.jsonl")).isEmpty();
        assertThat(ftsRows()).isZero();
    }

    @Test
    void writeTransactionRollsBackWhenTheWorkThrows() {
        assertThatThrownBy(() -> repository.inWriteTransaction(() -> {
            repository.createFile("/t/a.jsonl", "s1");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.findFile("/t/a.jsonl")).isEmpty();
    }

    @Test
    void writeTransactionReturnsTheWorkResult() {
        assertThat(repository.inWriteTransaction(() -> 42)).isEqualTo(42);
    }

    @Test
    void projectParamsCountCodePointsOfThePrefix() {
        assertThat(TranscriptIndexRepository.projectParams("/a", "/a/")).containsExactly("/a", 3, "/a/");
        assertThat(TranscriptIndexRepository.projectParams("/\uD83D\uDE00", "/\uD83D\uDE00/")).containsExactly("/\uD83D\uDE00", 3, "/\uD83D\uDE00/");
    }

    @Test
    void projectConditionAddsNocaseOnlyWhenAsked() {
        assertThat(TranscriptIndexRepository.projectCondition("cwd", false)).isEqualTo("(cwd = ? OR substr(cwd, 1, ?) = ?)");
        assertThat(TranscriptIndexRepository.projectCondition("cwd", true)).isEqualTo("(cwd = ? COLLATE NOCASE OR substr(cwd, 1, ?) = ? COLLATE NOCASE)");
    }
}
