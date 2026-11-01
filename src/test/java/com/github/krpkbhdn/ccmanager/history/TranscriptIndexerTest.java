package com.github.krpkbhdn.ccmanager.history;

import static com.github.krpkbhdn.ccmanager.history.HistoryFixture.toolCallLine;
import static com.github.krpkbhdn.ccmanager.history.HistoryFixture.toolResultLine;
import static com.github.krpkbhdn.ccmanager.history.HistoryFixture.userLine;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranscriptIndexerTest {

    private static final Optional<String> ANY_PROJECT = Optional.empty();

    @TempDir
    Path temp;

    HistoryFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new HistoryFixture(temp);
    }

    long hits(String query) {
        return fixture.search.search(query, ANY_PROJECT, Optional.empty(), Optional.empty()).size();
    }

    @Test
    void secondScanIndexesOnlyNewLines() {
        var file = fixture.transcript("p", "s1");
        fixture.write(file, userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "first banana"));
        var indexer = fixture.indexer(1000);
        indexer.scan();
        indexer.scan();
        assertThat(fixture.search.indexedEntryCount()).isEqualTo(1);

        fixture.append(file, userLine("u2", "/w/app", "2026-01-01T10:01:00Z", "second banana"));
        indexer.scan();

        assertThat(fixture.search.indexedEntryCount()).isEqualTo(2);
        assertThat(hits("banana")).isEqualTo(2);
    }

    @Test
    void unfinishedLineIsIndexedOnlyAfterItsNewline() {
        var file = fixture.transcript("p", "s1");
        var line = userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "pineapple");
        fixture.write(file, line.substring(0, line.length() - 1));
        var indexer = fixture.indexer(1000);
        indexer.scan();
        assertThat(hits("pineapple")).isZero();

        fixture.append(file, "\n");
        indexer.scan();

        assertThat(hits("pineapple")).isEqualTo(1);
    }

    @Test
    void rewrittenShorterFileIsIndexedAgainFromTheStart() {
        var file = fixture.transcript("p", "s1");
        fixture.write(file, userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "a very long original sentence about kiwi")
                + userLine("u2", "/w/app", "2026-01-01T10:01:00Z", "another kiwi"));
        var indexer = fixture.indexer(1000);
        indexer.scan();

        fixture.write(file, userLine("u3", "/w/app", "2026-01-01T10:02:00Z", "plum"));
        indexer.scan();

        assertThat(hits("kiwi")).isZero();
        assertThat(hits("plum")).isEqualTo(1);
        assertThat(fixture.search.indexedEntryCount()).isEqualTo(1);
    }

    @Test
    void subagentTranscriptsAreSkipped() {
        fixture.write(fixture.projects.resolve("p").resolve("s1").resolve("subagents").resolve("agent-1.jsonl"),
                userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "hidden"));

        fixture.indexer(1000).scan();

        assertThat(fixture.search.indexedEntryCount()).isZero();
    }

    @Test
    void oldestFilesAreEvictedOverTheLimitAndNotIndexedAgain() {
        fixture.write(fixture.transcript("p", "old"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "oldword")
                + userLine("u2", "/w/app", "2026-01-01T10:01:00Z", "oldword two"));
        fixture.write(fixture.transcript("p", "new"), userLine("u3", "/w/app", "2026-02-01T10:00:00Z", "newword")
                + userLine("u4", "/w/app", "2026-02-01T10:01:00Z", "newword two"));
        var indexer = fixture.indexer(3);

        indexer.scan();
        indexer.scan();

        assertThat(hits("oldword")).isZero();
        assertThat(hits("newword")).isEqualTo(2);
        assertThat(fixture.search.indexedEntryCount()).isEqualTo(2);
    }

    @Test
    void secretsAreMaskedInTheIndex() {
        fixture.write(fixture.transcript("p", "s1"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z",
                "deploy with sk-abcdefghijklmnopqrstuvwxyz123456 now"));

        fixture.indexer(1000).scan();

        assertThat(hits("abcdefghijklmnopqrstuvwxyz123456")).isZero();
        assertThat(hits("deploy")).isEqualTo(1);
    }

    @Test
    void resultOfAToolCallWhoseEnvFileComesAfterTheShownInputIsNotIndexed() {
        var command = "echo " + "x".repeat(500) + " && cat .env";
        fixture.write(fixture.transcript("p", "s1"),
                toolCallLine("a1", "/w/app", "2026-01-01T10:00:00Z", "t1", "{\"command\":\"" + command + "\"}")
                        + toolResultLine("u2", "/w/app", "2026-01-01T10:00:01Z", "t1", "DB_HOST=zebrahost"));

        fixture.indexer(1000).scan();

        assertThat(hits("zebrahost")).isZero();
    }

    @Test
    void fileRewrittenToTheSameOrLargerSizeIsReadAgainFromTheStart() {
        var file = fixture.transcript("p", "s1");
        fixture.write(file, userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "kiwi"));
        var indexer = fixture.indexer(1000);
        indexer.scan();

        fixture.write(file, userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "kiwi").replace("kiwi", "plum").stripTrailing()
                + " ".repeat(40) + "\n");
        indexer.scan();

        assertThat(hits("kiwi")).isZero();
        assertThat(hits("plum")).isEqualTo(1);
    }

    @Test
    void entriesOfADeletedTranscriptAreForgotten() throws Exception {
        var file = fixture.transcript("p", "s1");
        fixture.write(file, userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "kiwi"));
        var indexer = fixture.indexer(1000);
        indexer.scan();

        java.nio.file.Files.delete(file);
        indexer.scan();

        assertThat(hits("kiwi")).isZero();
        assertThat(fixture.search.indexedEntryCount()).isZero();
    }

    @Test
    void fileLargerThanOneBatchIsIndexedCompletely() {
        var content = new StringBuilder();
        var padding = "p".repeat(1000);
        for (int i = 0; i < 9_000; i++) {
            content.append(userLine("u" + i, "/w/app", "2026-01-01T10:00:00Z", "entry " + i + " " + padding));
        }
        fixture.write(fixture.transcript("p", "big"), content.toString());
        var indexer = fixture.indexer(100_000);

        indexer.scan();
        indexer.scan();

        assertThat(fixture.search.indexedEntryCount()).isEqualTo(9_000);
    }

    @Test
    void sessionIdPrefersTheUuidFileName() {
        var uuid = "123e4567-e89b-12d3-a456-426614174000";
        fixture.write(fixture.transcript("p", uuid), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "kiwi"));

        fixture.indexer(1000).scan();

        assertThat(fixture.search.search("kiwi", Optional.empty(), Optional.empty(), Optional.empty()))
                .extracting(SessionSearchService.SearchHit::claudeSessionId).containsExactly(uuid);
    }

    @Test
    void resultOfAToolCallOnAnEnvFileIsNotIndexed() {
        fixture.write(fixture.transcript("p", "s1"),
                toolCallLine("a1", "/w/app", "2026-01-01T10:00:00Z", "t1", "{\"file_path\":\"/w/app/.env\"}")
                        + toolResultLine("u2", "/w/app", "2026-01-01T10:00:01Z", "t1", "DB_HOST=zebrahost")
                        + toolCallLine("a3", "/w/app", "2026-01-01T10:00:02Z", "t2", "{\"file_path\":\"/w/app/readme.md\"}")
                        + toolResultLine("u4", "/w/app", "2026-01-01T10:00:03Z", "t2", "giraffe notes"));

        fixture.indexer(1000).scan();

        assertThat(hits("zebrahost")).isZero();
        assertThat(hits("giraffe")).isEqualTo(1);
    }
}
