package com.github.krpkbhdn.ccmanager.history;

import static com.github.krpkbhdn.ccmanager.history.HistoryFixture.userLine;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionSearchServiceTest {

    private static final Optional<Instant> ANY = Optional.empty();

    @TempDir
    Path temp;

    HistoryFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new HistoryFixture(temp);
    }

    private static String sep(String path) {
        return path.replace('/', File.separatorChar);
    }

    @Test
    void findsCyrillicTextWithMarkedSnippetAndPrefixOfLastWord() {
        fixture.write(fixture.transcript("p", "s1"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "Налаштувати пошук по історії"));
        fixture.indexer(1000).scan();

        var hits = fixture.search.search("налашт", Optional.empty(), ANY, ANY);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().role()).isEqualTo("user");
        assertThat(hits.getFirst().claudeSessionId()).isEqualTo("s1");
        assertThat(hits.getFirst().entryUuid()).isEqualTo("u1");
        assertThat(hits.getFirst().snippet()).contains("\u0002Налаштувати\u0003");
    }

    @Test
    void limitedSearchKeepsTheRankOrderOfTheUnlimitedQueryAndFilters() {
        var app = sep("/w/app");
        var other = sep("/w/other");
        var text = new StringBuilder();
        var otherText = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            var at = "2026-01-%02dT10:%02d:00Z".formatted(1 + i / 60, i % 60);
            text.append(userLine("a" + i, app, at, "needle " + "filler ".repeat(i)));
            otherText.append(userLine("o" + i, other, at, "needle " + "pad ".repeat(i)));
        }
        fixture.write(fixture.transcript("a", "s1"), text.toString());
        fixture.write(fixture.transcript("b", "s2"), otherText.toString());
        fixture.indexer(100000).scan();

        var expected = fixture.jdbc.sql("""
                SELECT e.entry_uuid FROM transcript_entry_fts e JOIN transcript_file f ON f.id = e.file_id
                WHERE transcript_entry_fts MATCH '"needle"*' AND f.cwd = ? AND e.at >= ?
                ORDER BY rank, e.at DESC LIMIT 100""").params(app, Instant.parse("2026-01-01T10:30:00Z").toEpochMilli())
                .query(String.class).list();
        var hits = fixture.search.search("needle", Optional.of(app), Optional.of(Instant.parse("2026-01-01T10:30:00Z")), ANY);

        assertThat(hits).extracting(SessionSearchService.SearchHit::entryUuid).containsExactlyElementsOf(expected);
        assertThat(hits).hasSize(100).allSatisfy(hit -> assertThat(hit.snippet()).contains("needle"));
    }

    @Test
    void tiedEntriesKeepAStableOrderUnderTheLimitWithAllFiltersCombined() {
        var app = sep("/w/app");
        var text = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            text.append(userLine("t%03d".formatted(i), app, "2026-01-05T10:00:00Z", "needle same text"));
        }
        text.append(userLine("early", app, "2025-01-01T10:00:00Z", "needle same text"));
        text.append(userLine("late", app, "2027-01-01T10:00:00Z", "needle same text"));
        fixture.write(fixture.transcript("a", "s1"), text.toString());
        fixture.indexer(100000).scan();
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");

        var expected = fixture.jdbc.sql("""
                SELECT e.entry_uuid FROM transcript_entry_fts e JOIN transcript_file f ON f.id = e.file_id
                WHERE transcript_entry_fts MATCH '"needle"*' AND f.cwd = ? AND e.at >= ? AND e.at <= ?
                ORDER BY rank, e.at DESC, e.rowid LIMIT 100""").params(app, from.toEpochMilli(), to.toEpochMilli())
                .query(String.class).list();
        var hits = fixture.search.search("needle", Optional.of(app), Optional.of(from), Optional.of(to));

        assertThat(hits).extracting(SessionSearchService.SearchHit::entryUuid).containsExactlyElementsOf(expected).hasSize(100);
    }

    @Test
    void queryWithSyntaxCharactersDoesNotFail() {
        fixture.write(fixture.transcript("p", "s1"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "plain text"));
        fixture.indexer(1000).scan();

        assertThat(fixture.search.search("\"a* OR (b", Optional.empty(), ANY, ANY)).isEmpty();
        assertThat(fixture.search.search("   ", Optional.empty(), ANY, ANY)).isEmpty();
    }

    @Test
    void projectFilterMatchesTheFolderAndItsSubfoldersOnly() {
        var app = sep("/w/app");
        fixture.write(fixture.transcript("a", "s1"), userLine("u1", app, "2026-01-01T10:00:00Z", "needle in app"));
        fixture.write(fixture.transcript("b", "s2"), userLine("u2", sep("/w/app/sub"), "2026-01-01T10:00:00Z", "needle in sub"));
        fixture.write(fixture.transcript("c", "s3"), userLine("u3", sep("/w/app2"), "2026-01-01T10:00:00Z", "needle in app2"));
        fixture.write(fixture.transcript("d", "s4"), userLine("u4", sep("/w/a_p"), "2026-01-01T10:00:00Z", "needle in underscore"));
        fixture.indexer(1000).scan();

        var inApp = fixture.search.search("needle", Optional.of(app), ANY, ANY);
        var wildcard = fixture.search.search("needle", Optional.of(sep("/w/a_")), ANY, ANY);

        assertThat(inApp).extracting(SessionSearchService.SearchHit::entryUuid).containsExactlyInAnyOrder("u1", "u2");
        assertThat(wildcard).isEmpty();
    }

    @Test
    void periodFilterKeepsEntriesInsideTheRange() {
        fixture.write(fixture.transcript("p", "s1"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "needle early")
                + userLine("u2", "/w/app", "2026-03-01T10:00:00Z", "needle late"));
        fixture.indexer(1000).scan();

        var hits = fixture.search.search("needle", Optional.empty(), Optional.of(Instant.parse("2026-02-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-04-01T00:00:00Z")));

        assertThat(hits).extracting(SessionSearchService.SearchHit::entryUuid).containsExactly("u2");
    }

    @Test
    void clearForgetsTheProjectEntriesAndTheyStayForgotten() {
        var app = sep("/w/app");
        fixture.write(fixture.transcript("a", "s1"), userLine("u1", app, "2026-01-01T10:00:00Z", "needle in app"));
        fixture.write(fixture.transcript("b", "s2"), userLine("u2", sep("/w/other"), "2026-01-01T10:00:00Z", "needle in other"));
        var indexer = fixture.indexer(1000);
        indexer.scan();

        fixture.search.clear(app);
        indexer.scan();

        assertThat(fixture.search.search("needle", Optional.empty(), ANY, ANY)).extracting(SessionSearchService.SearchHit::entryUuid)
                .containsExactly("u2");
        assertThat(fixture.search.indexedEntryCount()).isEqualTo(1);
    }

    @Test
    void linesAppendedAfterClearAreIndexedButClearedOnesAreNot() {
        var app = sep("/w/app");
        var file = fixture.transcript("a", "s1");
        fixture.write(file, userLine("u1", app, "2026-01-01T10:00:00Z", "needle before"));
        var indexer = fixture.indexer(1000);
        indexer.scan();
        fixture.append(file, userLine("u2", app, "2026-01-01T10:01:00Z", "needle unscanned"));

        fixture.search.clear(app);
        fixture.append(file, userLine("u3", app, "2026-01-01T10:02:00Z", "needle after"));
        indexer.scan();

        assertThat(fixture.search.search("needle", Optional.empty(), ANY, ANY)).extracting(SessionSearchService.SearchHit::entryUuid)
                .containsExactly("u3");
    }

    @Test
    void projectFilterAndClearAreCaseSensitiveOnALinuxHost(@TempDir Path other) {
        var linux = new HistoryFixture(other, false);
        linux.write(linux.transcript("a", "s1"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "needle"));
        linux.indexer(1000).scan();

        assertThat(linux.search.search("needle", Optional.of("/W/APP"), ANY, ANY)).isEmpty();
        assertThat(linux.search.search("needle", Optional.of("/w/app"), ANY, ANY)).hasSize(1);
        linux.search.clear("/W/APP");
        assertThat(linux.search.indexedEntryCount()).isEqualTo(1);
    }

    @Test
    void projectFilterMatchesPathsWithNonBmpCharacters() {
        var app = sep("/w/\uD83D\uDE00app");
        fixture.write(fixture.transcript("a", "s1"), userLine("u1", app + File.separator + "sub", "2026-01-01T10:00:00Z", "needle"));
        fixture.indexer(1000).scan();

        assertThat(fixture.search.search("needle", Optional.of(app), ANY, ANY)).hasSize(1);
    }

    @Test
    void queryOfOnlyPunctuationFindsNothingWithoutError() {
        fixture.write(fixture.transcript("p", "s1"), userLine("u1", "/w/app", "2026-01-01T10:00:00Z", "plain text"));
        fixture.indexer(1000).scan();

        assertThat(fixture.search.search("...", Optional.empty(), ANY, ANY)).isEmpty();
        assertThat(fixture.search.search("-- !?", Optional.empty(), ANY, ANY)).isEmpty();
    }

    @Test
    void transcriptWithoutCwdIsFoundWithAnEmptyCwd() {
        fixture.write(fixture.transcript("p", "s1"), userLine("u1", "", "2026-01-01T10:00:00Z", "needle"));
        fixture.indexer(1000).scan();

        assertThat(fixture.search.search("needle", Optional.empty(), ANY, ANY)).extracting(SessionSearchService.SearchHit::cwd)
                .containsExactly("");
    }

    @Test
    void clearAndProjectFilterIgnoreCaseOfTheProjectPath() {
        fixture.write(fixture.transcript("a", "s1"), userLine("u1", sep("/w/app"), "2026-01-01T10:00:00Z", "needle"));
        fixture.indexer(1000).scan();

        assertThat(fixture.search.search("needle", Optional.of(sep("/W/APP")), ANY, ANY)).hasSize(1);
        fixture.search.clear(sep("/W/APP"));

        assertThat(fixture.search.indexedEntryCount()).isZero();
    }

    @Test
    void searchesTenThousandEntriesInUnderASecond() {
        var content = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            content.append(userLine("u" + i, "/w/app", "2026-01-01T10:00:00Z", "entry number " + i + " about topic" + i % 50));
        }
        fixture.write(fixture.transcript("p", "big"), content.toString());
        fixture.indexer(100_000).scan();

        var start = System.nanoTime();
        var hits = fixture.search.search("topic7", Optional.empty(), ANY, ANY);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(fixture.search.indexedEntryCount()).isEqualTo(10_000);
        assertThat(hits).hasSize(100);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }
}
