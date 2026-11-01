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
