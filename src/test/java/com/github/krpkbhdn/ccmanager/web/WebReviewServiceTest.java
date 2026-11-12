package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.review.ReviewStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class WebReviewServiceTest {

    private static final String URL = "http://localhost:5173/settings?utm_source=x";
    private static final String KEY = "http://localhost:5173/settings";
    private static final byte[] PNG = TestPng.tiny();

    @TempDir
    Path temp;

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;
    WebReviewService service;
    long projectId;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        var properties = new WebReviewProperties(600, List.of("display"), 1024, 3);
        service = new WebReviewService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                new WebScreenshotStore(new AppProperties(temp), properties), new WebFeedbackWriter(properties));
        projectId = newProject("app");
    }

    private long newProject(String name) {
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES (?, ?, 'now')").param(name).param(temp.resolve(name).toString()).update();
        return jdbc.sql("SELECT id FROM project WHERE name = ?").param(name).query(Long.class).single();
    }

    private static ElementAnchor anchor() {
        return new ElementAnchor("button.save", "/html/body/button", "button", new ElementAnchor.SourceHint("Save", "Save.tsx", 42),
                new ElementAnchor.Rect(1, 2, 3, 4), "", "Save", "<button>", Map.of("display", "block"));
    }

    private WebItem add(String comment) {
        return service.addItem(projectId, URL, "Settings", anchor(), comment, List.of(new StyleChange("color", "#000", "#fff")));
    }

    @Test
    void addItemStoresAnchorChangesAndUpsertsPage() {
        var fired = new AtomicInteger();
        service.subscribe(fired::incrementAndGet);

        var first = add("one");
        var second = service.addItem(projectId, "http://localhost:5173/settings/", "New title", anchor(), "two", List.of());

        assertThat(first.n()).isEqualTo(1);
        assertThat(second.n()).isEqualTo(2);
        assertThat(second.pageId()).isEqualTo(first.pageId());
        assertThat(first.anchor()).isEqualTo(anchor());
        assertThat(first.styleChanges()).containsExactly(new StyleChange("color", "#000", "#fff"));
        assertThat(service.pages(projectId)).singleElement().satisfies(page -> {
            assertThat(page.pageKey()).isEqualTo(KEY);
            assertThat(page.title()).isEqualTo("New title");
            assertThat(page.lastUrl()).isEqualTo("http://localhost:5173/settings/");
        });
        assertThat(fired.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void addItemRejectsUnsafeUrlAndFullRevision() {
        assertThatThrownBy(() -> service.addItem(projectId, "javascript:alert(1)", "", anchor(), "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        add("1");
        add("2");
        add("3");
        assertThatThrownBy(() -> add("4")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3 items");
    }

    @Test
    void currentRevisionIsCreatedOnceAndStartNextNeedsNoPending() {
        var first = service.currentRevision(projectId);

        assertThat(service.currentRevision(projectId).id()).isEqualTo(first.id());
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.status()).isEqualTo(ReviewStatus.PENDING);
        assertThatThrownBy(() -> service.startNextRevision(projectId)).isInstanceOf(IllegalStateException.class);

        add("x");
        service.prepareFeedback(first.id());
        var next = service.startNextRevision(projectId);
        assertThat(next.number()).isEqualTo(2);
        assertThat(service.currentRevision(projectId).id()).isEqualTo(next.id());
        assertThat(service.revisions(projectId)).extracting(WebRevision::number).containsExactly(2, 1);
    }

    @Test
    void prepareFeedbackMarksSentAndRejectsEmptyRevision() throws Exception {
        var item = add("fix it");
        service.saveScreenshot(item.id(), PNG);
        var revision = service.currentRevision(projectId);

        var feedback = service.prepareFeedback(revision.id());

        assertThat(Files.readString(feedback.markdown())).contains("### 1  `button.save`", "comment: `fix it`", "shot: `item-1.png`");
        assertThat(service.findRevision(revision.id()).orElseThrow()).satisfies(r -> {
            assertThat(r.status()).isEqualTo(ReviewStatus.SENT);
            assertThat(r.sentAt()).isNotNull();
        });
        assertThat(service.allItems(revision.id())).extracting(WebItem::status).containsExactly(WebItemStatus.SENT);
        assertThatThrownBy(() -> service.prepareFeedback(revision.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("already sent");
        var emptyNext = service.startNextRevision(projectId);
        assertThatThrownBy(() -> service.prepareFeedback(emptyNext.id())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no open items");
    }

    @Test
    void approveSettlesSentItemsAndKeepsThemInOverlay() {
        var item = add("a");
        var revision = service.currentRevision(projectId);
        service.prepareFeedback(revision.id());

        service.approve(revision.id());

        assertThat(service.findRevision(revision.id()).orElseThrow().status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(service.items(projectId, KEY)).extracting(WebItem::id, WebItem::status).containsExactly(
                org.assertj.core.groups.Tuple.tuple(item.id(), WebItemStatus.DONE));
    }

    @Test
    void reopeningAnOlderItemMovesItToTheCurrentRevisionWithStableNumber() {
        var item = add("a");
        var first = service.currentRevision(projectId);
        service.prepareFeedback(first.id());
        service.approve(first.id());

        service.setStatus(item.id(), WebItemStatus.OPEN);

        var current = service.currentRevision(projectId);
        assertThat(current.number()).isEqualTo(2);
        assertThat(service.allItems(current.id())).singleElement().satisfies(moved -> {
            assertThat(moved.id()).isEqualTo(item.id());
            assertThat(moved.n()).isEqualTo(1);
            assertThat(moved.status()).isEqualTo(WebItemStatus.OPEN);
        });
        assertThat(service.allItems(first.id())).isEmpty();
        assertThat(add("b").n()).isEqualTo(2);
    }

    @Test
    void dismissedItemsLeaveTheOverlayAndDeleteRemovesRowAndScreenshot() {
        var item = add("a");
        service.saveScreenshot(item.id(), PNG);
        var file = temp.resolve("web-review/" + projectId + "/" + item.revisionId() + "/item-1.png");
        assertThat(file).exists();

        service.setStatus(item.id(), WebItemStatus.DISMISSED);
        assertThat(service.items(projectId, KEY)).isEmpty();

        service.deleteItem(item.id());
        assertThat(service.allItems(item.revisionId())).isEmpty();
        assertThat(file).doesNotExist();
    }

    @Test
    void updateItemChangesCommentAndStyleChanges() {
        var item = add("a");

        service.updateItem(item.id(), "b", List.of());

        assertThat(service.allItems(item.revisionId())).singleElement().satisfies(updated -> {
            assertThat(updated.comment()).isEqualTo("b");
            assertThat(updated.styleChanges()).isEmpty();
        });
    }

    @Test
    void concurrentAddsGetDistinctNumbers() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            var futures = java.util.stream.IntStream.range(0, 3).mapToObj(i -> pool.submit(() -> add("c" + i).n())).toList();
            var numbers = new java.util.ArrayList<Integer>();
            for (var future : futures) {
                numbers.add(future.get());
            }
            assertThat(numbers).containsExactlyInAnyOrder(1, 2, 3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void saveScreenshotAfterDeleteLeavesNoFile() {
        var item = add("a");
        service.deleteItem(item.id());

        assertThatThrownBy(() -> service.saveScreenshot(item.id(), PNG)).isInstanceOf(IllegalArgumentException.class);
        assertThat(temp.resolve("web-review/" + projectId + "/" + item.revisionId() + "/item-1.png")).doesNotExist();
    }

    @Test
    void saveScreenshotRejectsNonPng() {
        var item = add("a");

        assertThatThrownBy(() -> service.saveScreenshot(item.id(), new byte[] {1, 2})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reopenIsRefusedWhenTheCurrentRevisionIsFull() {
        var old = add("old");
        service.prepareFeedback(service.currentRevision(projectId).id());
        service.startNextRevision(projectId);
        add("1");
        add("2");
        add("3");

        assertThatThrownBy(() -> service.setStatus(old.id(), WebItemStatus.OPEN)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3 items");
    }

    @Test
    void nullCommentOrChangesAreRejected() {
        assertThatThrownBy(() -> service.addItem(projectId, URL, "", anchor(), null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addItem(projectId, URL, "", anchor(), "", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secondPrepareOnTheSameRevisionIsRefused() {
        add("a");
        var revision = service.currentRevision(projectId);
        service.prepareFeedback(revision.id());

        assertThatThrownBy(() -> service.prepareFeedback(revision.id())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void itemAddedAfterPrepareGoesToTheNextRevisionAndNotIntoTheSentMarkdown() throws Exception {
        add("first");
        var revision = service.currentRevision(projectId);
        var feedback = service.prepareFeedback(revision.id());

        var late = add("late");

        assertThat(late.revisionId()).isNotEqualTo(revision.id());
        assertThat(service.allItems(late.revisionId())).extracting(WebItem::status).containsExactly(WebItemStatus.OPEN);
        assertThat(Files.readString(feedback.markdown())).contains("first").doesNotContain("late");
    }

    @Test
    void failedMarkdownWriteRollsTheStatusChangesBack() {
        var failing = org.mockito.Mockito.mock(WebFeedbackWriter.class);
        org.mockito.Mockito.when(failing.write(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenThrow(new java.io.UncheckedIOException(new java.io.IOException("disk")));
        var properties = new WebReviewProperties(600, List.of("display"), 1024, 3);
        var service = new WebReviewService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                new WebScreenshotStore(new AppProperties(temp), properties), failing);
        var item = service.addItem(projectId, URL, "", anchor(), "x", List.of());

        assertThatThrownBy(() -> service.prepareFeedback(item.revisionId())).isInstanceOf(java.io.UncheckedIOException.class);

        assertThat(service.findRevision(item.revisionId()).orElseThrow().status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(service.allItems(item.revisionId())).extracting(WebItem::status).containsExactly(WebItemStatus.OPEN);
    }

    @Test
    void approveRequiresASentRevision() {
        add("a");
        var revision = service.currentRevision(projectId);

        assertThatThrownBy(() -> service.approve(revision.id())).isInstanceOf(IllegalStateException.class);
        assertThat(service.allItems(revision.id())).extracting(WebItem::status).containsExactly(WebItemStatus.OPEN);
    }

    @Test
    void setStatusAllowsOnlyDismissAndReopen() {
        var item = add("a");

        assertThatThrownBy(() -> service.setStatus(item.id(), WebItemStatus.DONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.setStatus(item.id(), WebItemStatus.SENT)).isInstanceOf(IllegalStateException.class);
        service.setStatus(item.id(), WebItemStatus.DISMISSED);
        service.setStatus(item.id(), WebItemStatus.OPEN);
        assertThat(service.allItems(item.revisionId())).extracting(WebItem::status).containsExactly(WebItemStatus.OPEN);
    }

    @Test
    void storedPageUrlIsTheRealNavigableUrl() {
        service.addItem(projectId, "http://me:pw@localhost:5173/a?token=abc&x=1", "", anchor(), "", List.of());

        assertThat(service.pages(projectId)).singleElement().satisfies(page -> {
            assertThat(page.lastUrl()).isEqualTo("http://me:pw@localhost:5173/a?token=abc&x=1");
            assertThat(page.pageKey()).isEqualTo("http://localhost:5173/a?token=abc&x=1");
        });
    }

    @Test
    void touchPageUpsertsWithoutTouchingRevisionsAndFiresOnlyOnChange() {
        var fired = new AtomicInteger();
        service.subscribe(fired::incrementAndGet);

        service.touchPage(projectId, "http://localhost:5173/a", "A");
        service.touchPage(projectId, "http://localhost:5173/a", "A");
        service.touchPage(projectId, "http://localhost:5173/a/", "A2");

        assertThat(fired.get()).isEqualTo(2);
        assertThat(service.revisions(projectId)).isEmpty();
        assertThat(service.pages(projectId)).singleElement().satisfies(page -> {
            assertThat(page.title()).isEqualTo("A2");
            assertThat(page.lastUrl()).isEqualTo("http://localhost:5173/a/");
        });
    }

    @Test
    void browserLimitsComeFromTheProperties() {
        assertThat(service.browserLimits()).isEqualTo(new BrowserLimits(3, Duration.ofMinutes(10)));
    }

    @Test
    void updateItemRequiresAnExistingOpenItemAndFiresOnlyForRealChanges() {
        var fired = new AtomicInteger();
        var item = add("a");
        service.subscribe(fired::incrementAndGet);

        assertThatThrownBy(() -> service.updateItem(9999, "x", List.of())).isInstanceOf(IllegalArgumentException.class);
        service.setStatus(item.id(), WebItemStatus.DISMISSED);
        fired.set(0);
        assertThatThrownBy(() -> service.updateItem(item.id(), "x", List.of())).isInstanceOf(IllegalStateException.class);

        assertThat(fired.get()).isZero();
    }

    @Test
    void aCorruptItemRowIsSkippedAndTheRestStillLoads() {
        var good = add("good");
        var bad = add("bad");
        jdbc.sql("UPDATE web_review_item SET anchor_json = 'not json' WHERE id = ?").param(bad.id()).update();
        jdbc.sql("UPDATE web_review_item SET style_changes_json = '{' WHERE id = ?").param(good.id()).update();
        var third = add("third");

        assertThat(service.allItems(third.revisionId())).extracting(WebItem::id).containsExactly(third.id());
        assertThat(service.items(projectId, KEY)).extracting(WebItem::id).containsExactly(third.id());
        service.deleteItem(bad.id());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_review_item").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void deleteApprovedBeforePrunesOnlyOldApprovedRevisionsWithTheirFiles() throws Exception {
        var item = add("old");
        service.saveScreenshot(item.id(), PNG);
        var oldRevision = service.currentRevision(projectId);
        var feedback = service.prepareFeedback(oldRevision.id());
        service.approve(oldRevision.id());
        var sent = service.startNextRevision(projectId);
        var sentItem = add("sent");
        service.prepareFeedback(sent.id());
        var pending = service.startNextRevision(projectId);
        add("pending");
        jdbc.sql("UPDATE web_review_revision SET settled_at = '2020-01-01 00:00:00' WHERE id = ?").param(oldRevision.id()).update();
        jdbc.sql("UPDATE web_review_revision SET settled_at = '2020-01-01 00:00:00' WHERE id = ?").param(sent.id()).update();
        var folder = temp.resolve("web-review/" + projectId + "/" + oldRevision.id());
        assertThat(folder).exists();

        var removed = service.deleteApprovedBefore(java.time.Instant.parse("2021-01-01T00:00:00Z"));

        assertThat(removed).isEqualTo(1);
        assertThat(service.findRevision(oldRevision.id())).isEmpty();
        assertThat(folder).doesNotExist();
        assertThat(feedback.markdown()).doesNotExist();
        assertThat(service.findRevision(sent.id())).isPresent();
        assertThat(service.findRevision(pending.id())).isPresent();
        assertThat(service.allItems(sent.id())).extracting(WebItem::id).containsExactly(sentItem.id());
    }

    @Test
    void pagesAreOrderedByLastVisit() {
        service.addItem(projectId, "http://localhost:5173/a", "", anchor(), "", List.of());
        service.addItem(projectId, "http://localhost:5173/b", "", anchor(), "", List.of());
        jdbc.sql("UPDATE web_review_page SET last_visited_at = '2024-01-01 00:00:01.000' WHERE page_key LIKE '%/b'").update();
        jdbc.sql("UPDATE web_review_page SET last_visited_at = '2024-01-01 00:00:00.000' WHERE page_key LIKE '%/a'").update();
        service.touchPage(projectId, "http://localhost:5173/a", "");

        assertThat(service.pages(projectId)).extracting(WebPage::pageKey).containsExactly("http://localhost:5173/b", "http://localhost:5173/a");
    }

    @Test
    void bindSessionIsStored() {
        var revision = service.currentRevision(projectId);

        service.bindSession(revision.id(), "s1");

        assertThat(service.findRevision(revision.id()).orElseThrow().ccmSessionId()).isEqualTo("s1");
    }

    @Test
    void removingTheProjectCascades() {
        add("a");

        jdbc.sql("DELETE FROM project WHERE id = ?").param(projectId).update();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_review_revision").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_review_page").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM web_review_item").query(Integer.class).single()).isZero();
    }

    @Test
    void numbersAreCountedPerProject() {
        add("a");
        var other = newProject("other");

        var item = service.addItem(other, URL, "", anchor(), "", List.of());

        assertThat(item.n()).isEqualTo(1);
    }

    @Test
    void viewportRoundTripsAndOldItemsReadNull() {
        var viewport = new WebViewport(390, 844, "iPhone 14", DeviceCategory.MOBILE);

        var withViewport = service.addItem(projectId, URL, "", anchor(), "a", List.of(), viewport);
        var free = service.addItem(projectId, URL, "", anchor(), "b", List.of(), new WebViewport(1280, 720, null, DeviceCategory.DESKTOP));
        var without = add("c");

        assertThat(withViewport.viewport()).isEqualTo(viewport);
        assertThat(free.viewport()).isEqualTo(new WebViewport(1280, 720, null, DeviceCategory.DESKTOP));
        assertThat(without.viewport()).isNull();
    }

    @Test
    void viewportRejectsNonPositiveOrHugeSizes() {
        assertThatThrownBy(() -> new WebViewport(0, 10, null, DeviceCategory.MOBILE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebViewport(10, WebViewport.MAX_SIDE + 1, null, DeviceCategory.MOBILE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebViewport(10, 10, "x".repeat(WebViewport.MAX_NAME_CHARS + 1), DeviceCategory.MOBILE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
