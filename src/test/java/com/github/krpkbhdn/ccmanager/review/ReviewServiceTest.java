package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ReviewServiceTest {

    private static final int MAX_REVIEWS = 3;

    @TempDir
    Path temp;

    ReviewService service;
    JdbcClient jdbc;
    ReviewImageStore store = mock(ReviewImageStore.class);
    ReviewFeedbackWriter writer = mock(ReviewFeedbackWriter.class);
    long projectId;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        var properties = new ReviewProperties(java.time.Duration.ofDays(7), java.time.Duration.ofHours(1), 20, 1024, 8192, 36_000_000L, MAX_REVIEWS);
        service = new ReviewService(jdbc, store, writer, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                temp.resolve("reviews"));
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        projectId = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        when(store.store(any(), any())).thenAnswer(call -> {
            var dir = call.<Path>getArgument(0);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("1.png"), "x");
            return List.of(new StoredImage("1.png", "first", 10, 20), new StoredImage("2.png", "second", 30, 40));
        });
    }

    private Review submit(String title) {
        return service.submit(projectId, "s1", title, "note", List.of(new SubmittedImage(temp.resolve("a.png"), "first")));
    }

    @Test
    void submitStoresRowsAndFolderAndFiresListeners() {
        var fired = new AtomicInteger();
        service.subscribe(fired::incrementAndGet);

        var review = submit(" Title ");

        assertThat(review.title()).isEqualTo("Title");
        assertThat(review.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(review.createdAt()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        assertThat(service.images(review.id())).extracting(ReviewImage::position, ReviewImage::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "first"), org.assertj.core.groups.Tuple.tuple(1, "second"));
        assertThat(service.imagePath(service.images(review.id()).get(0))).exists();
        assertThat(service.knownIds()).containsExactly(review.id());
        assertThat(fired).hasValue(1);
    }

    @Test
    void submitRejectsBlankTitleAndCleansUpWhenStoringFails() {
        assertThatThrownBy(() -> submit(" ")).isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("bad")).when(store).store(any(), any());

        assertThatThrownBy(() -> submit("t")).isInstanceOf(IllegalArgumentException.class).hasMessage("bad");

        assertThat(service.list(projectId)).isEmpty();
    }

    @Test
    void submitRejectsWhenTheProjectAlreadyHasTheMaximumOfReviews() {
        for (int i = 0; i < MAX_REVIEWS; i++) {
            submit("r" + i);
        }

        assertThatThrownBy(() -> submit("one too many")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("too many reviews are waiting in this project: ask the user to delete some in the Review tab");

        assertThat(service.list(projectId)).hasSize(MAX_REVIEWS);
    }

    private ReviewService realStoreService() {
        return realStoreService(20);
    }

    private ReviewService realStoreService(int maxImages) {
        var properties = new ReviewProperties(java.time.Duration.ofDays(7), java.time.Duration.ofHours(1), maxImages, 1_000_000, 8192, 36_000_000L, 10);
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        return new ReviewService(jdbc, new ReviewImageStore(properties), writer, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                properties, temp.resolve("reviews"));
    }

    private static byte[] png() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 3, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private long captureFilesLeft() throws Exception {
        var captures = temp.resolve("reviews").resolve(ReviewService.CAPTURE_FOLDER);
        try (var files = Files.list(captures)) {
            return files.count();
        }
    }

    @Test
    void staleCaptureFilesAreSweptAndFreshOnesKept() throws Exception {
        var captures = Files.createDirectories(temp.resolve("reviews").resolve(ReviewService.CAPTURE_FOLDER));
        var stale = Files.writeString(captures.resolve("capture-old.img"), "x");
        var fresh = Files.writeString(captures.resolve("capture-new.img"), "x");
        Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(7200)));

        service.deleteStaleCaptures(Instant.now().minusSeconds(3600));

        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
    }

    @Test
    void aFailedCaptureStoreDoesNotLeakThePathInTheMessage() throws Exception {
        Files.createDirectories(temp.resolve("reviews"));
        Files.writeString(temp.resolve("reviews").resolve(ReviewService.CAPTURE_FOLDER), "a file where the folder should be");

        assertThatThrownBy(() -> service.submitCapture(projectId, "t", "", new byte[] {1}, "l"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(temp.toString());
    }

    @Test
    void submitCaptureStoresTheImageWithoutSessionAndRemovesTheTempFile() throws Exception {
        var review = realStoreService().submitCapture(projectId, "Page", "note", png(), "Desktop");

        assertThat(review.ccmSessionId()).isNull();
        var images = service.images(review.id());
        assertThat(images).extracting(ReviewImage::label, ReviewImage::width, ReviewImage::height).containsExactly(org.assertj.core.groups.Tuple.tuple("Desktop", 4, 3));
        assertThat(service.imagePath(images.get(0))).exists();
        assertThat(captureFilesLeft()).isZero();
    }

    @Test
    void submitCaptureRejectsBytesThatAreNoImageAndLeavesNothingBehind() throws Exception {
        assertThatThrownBy(() -> realStoreService().submitCapture(projectId, "Page", "", "not an image".getBytes(), "x"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.list(projectId)).isEmpty();
        assertThat(captureFilesLeft()).isZero();
    }

    @Test
    void submitCaptureSanitisesAndCapsTheUntrustedTitleAndNote() {
        var review = service.submitCapture(projectId, "evil\n‮title " + "a".repeat(300), "n\r\n" + "b".repeat(2000), new byte[] {1}, "l");

        assertThat(review.title()).doesNotContain("\n", "‮").hasSize(120);
        assertThat(review.note()).doesNotContain("\n").hasSize(1000);
    }

    @Test
    void submitCaptureAtTheProjectCapAsksTheUser() {
        for (int i = 0; i < MAX_REVIEWS; i++) {
            submit("r" + i);
        }

        assertThatThrownBy(() -> service.submitCapture(projectId, "t", "", new byte[] {1}, "l")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Too many reviews");
    }

    private long filesIn(Path folder) throws Exception {
        try (var files = Files.list(folder)) {
            return files.count();
        }
    }

    @Test
    void addCaptureAppendsAndKeepsBothFilesWithPositionsZeroAndOne() throws Exception {
        var real = realStoreService();
        var fired = new AtomicInteger();
        real.subscribe(fired::incrementAndGet);
        var review = real.submitCapture(projectId, "Page", "", png(), "first");

        var after = real.addCapture(review.id(), projectId, png(), "second");

        assertThat(after.id()).isEqualTo(review.id());
        var images = real.images(review.id());
        assertThat(images).extracting(ReviewImage::position, ReviewImage::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "first"), org.assertj.core.groups.Tuple.tuple(1, "second"));
        assertThat(images).allSatisfy(image -> assertThat(real.imagePath(image)).exists());
        assertThat(images.get(0).fileName()).isNotEqualTo(images.get(1).fileName());
        assertThat(fired).hasValue(2);
        assertThat(captureFilesLeft()).isZero();
    }

    @Test
    void addCaptureIsRefusedForSettledMissingFullAndForeignReviewsAndLeavesNoFile() throws Exception {
        var real = realStoreService(1);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('other', 'x', 'now')").update();
        var otherProject = jdbc.sql("SELECT id FROM project WHERE name = 'other'").query(Long.class).single();
        var full = real.submitCapture(projectId, "full", "", png(), "a");
        var sent = real.submitCapture(projectId, "sent", "", png(), "a");
        var approved = real.submitCapture(projectId, "approved", "", png(), "a");
        var deleted = real.submitCapture(projectId, "deleted", "", png(), "a");
        real.approve(approved.id());
        jdbc.sql("UPDATE review SET status = 'SENT' WHERE id = ?").param(sent.id()).update();
        real.delete(deleted.id());

        assertRefused(() -> real.addCapture(full.id(), projectId, png(), "b"), ReviewService.Reason.FULL);
        assertRefused(() -> real.addCapture(sent.id(), projectId, png(), "b"), ReviewService.Reason.SETTLED);
        assertRefused(() -> real.addCapture(approved.id(), projectId, png(), "b"), ReviewService.Reason.SETTLED);
        assertRefused(() -> real.addCapture(deleted.id(), projectId, png(), "b"), ReviewService.Reason.MISSING);
        assertRefused(() -> real.addCapture(full.id(), otherProject, png(), "b"), ReviewService.Reason.MISSING);

        assertThat(filesIn(real.directoryOf(full.id()))).isEqualTo(1);
        assertThat(filesIn(real.directoryOf(sent.id()))).isEqualTo(1);
        assertThat(real.directoryOf(deleted.id())).doesNotExist();
        assertThat(captureFilesLeft()).isZero();
    }

    private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ReviewService.Reason reason) {
        assertThatThrownBy(call).isInstanceOf(ReviewService.CaptureRefusedException.class)
                .satisfies(e -> assertThat(((ReviewService.CaptureRefusedException) e).reason()).isEqualTo(reason));
    }

    @Test
    void captureTargetsListPendingReviewsNewestFirstWithCounts() throws Exception {
        var real = realStoreService(2);
        var older = real.submitCapture(projectId, "older", "", png(), "a");
        var newer = real.submitCapture(projectId, "newer", "", png(), "a");
        real.addCapture(newer.id(), projectId, png(), "b");
        var sent = real.submitCapture(projectId, "sent", "", png(), "a");
        var approved = real.submitCapture(projectId, "approved", "", png(), "a");
        jdbc.sql("UPDATE review SET status = 'SENT' WHERE id = ?").param(sent.id()).update();
        real.approve(approved.id());

        var targets = real.captureTargets(projectId);

        assertThat(targets).extracting(ReviewService.CaptureTarget::id, ReviewService.CaptureTarget::images, ReviewService.CaptureTarget::maxImages,
                ReviewService.CaptureTarget::full).containsExactly(
                org.assertj.core.groups.Tuple.tuple(newer.id(), 2, 2, true), org.assertj.core.groups.Tuple.tuple(older.id(), 1, 2, false));
        assertThat(targets.get(0).title()).isEqualTo("newer");
        assertThat(real.captureTargets(projectId + 100)).isEmpty();
    }

    @Test
    void submitCaptureCapsTheLabelLikeTheTitle() throws Exception {
        var review = realStoreService().submitCapture(projectId, "t", "", png(), "l".repeat(300));

        assertThat(service.images(review.id()).get(0).label()).hasSize(120);
    }

    @Test
    void limitsExposeTheStoreCaps() {
        assertThat(service.limits()).isEqualTo(new ReviewService.CaptureLimits(8192, 36_000_000L, 1024));
    }

    @Test
    void failedCleanupDoesNotMaskTheOriginalFailure() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("bad")).when(store).store(any(), any());
        jdbc.sql("CREATE TRIGGER no_delete BEFORE DELETE ON review BEGIN SELECT RAISE(ABORT, 'boom'); END").update();

        assertThatThrownBy(() -> submit("t")).isInstanceOf(IllegalArgumentException.class).hasMessage("bad")
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    void saveMarksOnADeletedReviewSaysSo() {
        var review = submit("t");
        var imageId = service.images(review.id()).get(0).id();
        service.delete(review.id());

        assertThatThrownBy(() -> service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.PIN, 0.5, 0.5, 0, 0, "c"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("the review was deleted");
    }

    @Test
    void listIsNewestFirst() {
        var first = submit("one");
        var second = submit("two");

        assertThat(service.list(projectId)).extracting(Review::id).containsExactly(second.id(), first.id());
        assertThat(service.find(first.id())).isPresent();
    }

    @Test
    void saveMarksReplacesAllAndClampsCoordinates() {
        var imageId = service.images(submit("t").id()).get(0).id();
        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.RECTANGLE, 0.1, 0.2, 0.3, 0.4, "a")));

        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.RECTANGLE, -1, 2, 5, -3, "b"), new MarkDraft(MarkKind.PIN, 0.5, 0.5, 0.2, 0.2, "c")));

        assertThat(service.marks(imageId)).extracting(ReviewMark::comment, ReviewMark::x, ReviewMark::y, ReviewMark::w, ReviewMark::h).containsExactly(
                org.assertj.core.groups.Tuple.tuple("b", 0.0, 1.0, 1.0, 0.0), org.assertj.core.groups.Tuple.tuple("c", 0.5, 0.5, 0.0, 0.0));
    }

    @Test
    void freeformRoundTripsPointsClampsThemAndRecomputesTheBoundingBox() {
        var imageId = service.images(submit("t").id()).get(0).id();
        var points = List.of(new MarkPoint(0.2, 0.3), new MarkPoint(0.6, 0.4), new MarkPoint(1.5, 0.9));

        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.FREEFORM, 0, 0, 0, 0, "f", points)));

        var mark = service.marks(imageId).get(0);
        assertThat(mark.points()).containsExactly(new MarkPoint(0.2, 0.3), new MarkPoint(0.6, 0.4), new MarkPoint(1.0, 0.9));
        assertThat(mark).extracting(ReviewMark::x, ReviewMark::y, ReviewMark::w, ReviewMark::h).containsExactly(0.2, 0.3, 0.8, 0.6000000000000001);
    }

    @Test
    void freeformWithFewerThanThreePointsIsRejectedAndOtherKindsDropPoints() {
        var imageId = service.images(submit("t").id()).get(0).id();
        var two = List.of(new MarkPoint(0.1, 0.1), new MarkPoint(0.2, 0.2));

        assertThatThrownBy(() -> service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.FREEFORM, 0, 0, 0, 0, "f", two))))
                .isInstanceOf(IllegalArgumentException.class);
        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.RECTANGLE, 0.1, 0.1, 0.2, 0.2, "r", two)));

        assertThat(service.marks(imageId).get(0).points()).isEmpty();
    }

    @Test
    void migrationToV21KeepsExistingMarksAndAcceptsFreeform() {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("old.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).target("20").load().migrate();
        var db = JdbcClient.create(dataSource);
        db.sql("INSERT INTO project (name, path, created_at) VALUES ('p', 'x', 'now')").update();
        db.sql("INSERT INTO review (project_id, title) VALUES (1, 't')").update();
        db.sql("INSERT INTO review_image (review_id, position, file_name, width, height) VALUES (1, 0, 'a.png', 1, 1)").update();
        db.sql("INSERT INTO review_mark (image_id, kind, x, y, w, h, comment) VALUES (1, 'PIN', 0.5, 0.5, 0, 0, 'old')").update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(db.sql("SELECT comment || points FROM review_mark").query(String.class).list()).containsExactly("old[]");
        db.sql("INSERT INTO review_mark (image_id, kind, x, y, w, h) VALUES (1, 'FREEFORM', 0, 0, 1, 1)").update();
        assertThat(db.sql("SELECT COUNT(*) FROM review_image").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void failedSaveMarksKeepsTheOldMarks() {
        var imageId = service.images(submit("t").id()).get(0).id();
        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.PIN, 0.5, 0.5, 0, 0, "old")));
        jdbc.sql("CREATE TRIGGER fail_second BEFORE INSERT ON review_mark WHEN NEW.comment = 'bad' BEGIN SELECT RAISE(ABORT, 'boom'); END").update();

        assertThatThrownBy(() -> service.saveMarks(imageId, List.of(
                new MarkDraft(MarkKind.PIN, 0.1, 0.1, 0, 0, "new"), new MarkDraft(MarkKind.PIN, 0.2, 0.2, 0, 0, "bad"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(service.marks(imageId)).extracting(ReviewMark::comment).containsExactly("old");
    }

    private List<ReviewMark> marksWithStoredPoints(String kind, String points) {
        var imageId = service.images(submit("t").id()).get(0).id();
        jdbc.sql("INSERT INTO review_mark (image_id, kind, x, y, w, h, comment, points) VALUES (?, ?, 0.1, 0.2, 0.3, 0.4, 'c', ?)")
                .param(imageId).param(kind).param(points).update();
        return service.marks(imageId);
    }

    @Test
    void malformedPointsJsonDoesNotBreakMarks() {
        var marks = marksWithStoredPoints("FREEFORM", "{not json");

        assertThat(marks).hasSize(1);
        assertThat(marks.get(0).points()).hasSize(4);
    }

    @Test
    void shortPairNanAndWrongShapeAreDropped() {
        var marks = marksWithStoredPoints("FREEFORM", "[[0.1],[\"NaN\",0.2],[0.1,0.1],[0.2,0.2],[0.3,0.1],\"x\"]");

        assertThat(marks.get(0).points()).containsExactly(new MarkPoint(0.1, 0.1), new MarkPoint(0.2, 0.2), new MarkPoint(0.3, 0.1));
        assertThat(marksWithStoredPoints("PIN", "{}").get(0).points()).isEmpty();
    }

    @Test
    void outOfRangePointsAreClamped() {
        var marks = marksWithStoredPoints("FREEFORM", "[[-1,0.5],[2,0.5],[0.5,9]]");

        assertThat(marks.get(0).points()).containsExactly(new MarkPoint(0, 0.5), new MarkPoint(1, 0.5), new MarkPoint(0.5, 1));
    }

    @Test
    void freeformWithFewerThanThreeValidPointsGetsTheBoundingBoxPolygon() {
        var mark = marksWithStoredPoints("FREEFORM", "[[0.1,0.1],[0.2]]").get(0);

        assertThat(mark.kind()).isEqualTo(MarkKind.FREEFORM);
        assertThat(mark.points()).containsExactly(new MarkPoint(0.1, 0.2), new MarkPoint(0.1 + 0.3, 0.2),
                new MarkPoint(0.1 + 0.3, 0.2 + 0.4), new MarkPoint(0.1, 0.2 + 0.4));
    }

    @Test
    void invalidFreeformInTheListKeepsTheOldMarks() {
        var imageId = service.images(submit("t").id()).get(0).id();
        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.PIN, 0.5, 0.5, 0, 0, "old")));
        var invalid = new MarkDraft(MarkKind.FREEFORM, 0, 0, 0, 0, "f", List.of(new MarkPoint(0.1, 0.1)));

        assertThatThrownBy(() -> service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.PIN, 0.1, 0.1, 0, 0, "new"), invalid)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.marks(imageId)).extracting(ReviewMark::comment).containsExactly("old");
    }

    @Test
    void prepareFeedbackWritesThenMarksReviewSent() {
        var review = submit("t");
        var feedback = new ReviewFeedback(temp.resolve("f.md"), "prompt");
        when(writer.write(any(), any(), any(), any())).thenReturn(feedback);

        assertThat(service.prepareFeedback(review.id())).isSameAs(feedback);

        var after = service.find(review.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(ReviewStatus.SENT);
        assertThat(after.settledAt()).isNotNull();
        assertThatThrownBy(() -> service.prepareFeedback(999)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveSettlesWithoutFeedback() {
        var review = submit("t");

        service.approve(review.id());

        assertThat(service.find(review.id()).orElseThrow().status()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void deleteRemovesRowsAndFolder() {
        var review = submit("t");

        service.delete(review.id());

        assertThat(service.find(review.id())).isEmpty();
        assertThat(service.directoryOf(review.id())).doesNotExist();
    }

    @Test
    void deleteOlderThanRemovesOnlyOldReviews() {
        var old = submit("old");
        var fresh = submit("fresh");
        jdbc.sql("UPDATE review SET created_at = '2020-01-01 00:00:00' WHERE id = ?").param(old.id()).update();

        assertThat(service.deleteOlderThan(Instant.now().minusSeconds(3600))).isEqualTo(1);

        assertThat(service.knownIds()).containsExactly(fresh.id());
        assertThat(service.directoryOf(old.id())).doesNotExist();
    }

    @Test
    void deletingTheProjectCascadesToReviewsImagesAndMarks() {
        var imageId = service.images(submit("t").id()).get(0).id();
        service.saveMarks(imageId, List.of(new MarkDraft(MarkKind.PIN, 0.5, 0.5, 0, 0, "c")));

        jdbc.sql("DELETE FROM project WHERE id = ?").param(projectId).update();

        assertThat(service.knownIds()).isEmpty();
        assertThat(service.marks(imageId)).isEmpty();
    }
}
