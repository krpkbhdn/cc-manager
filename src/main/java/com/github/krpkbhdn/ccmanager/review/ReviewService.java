package com.github.krpkbhdn.ccmanager.review;

import com.github.krpkbhdn.ccmanager.workspace.ChangeListeners;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Visual reviews: screenshots with marks, stored in SQLite and under a temp folder per review. */
@Service
@EnableConfigurationProperties(ReviewProperties.class)
public class ReviewService {

    private static final Path DEFAULT_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "cc-manager", "reviews");
    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);
    private static final int MIN_FREEFORM_POINTS = 3;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SELECT = "SELECT id, project_id, ccm_session_id, title, note, status, created_at, settled_at FROM review";
    private static final DateTimeFormatter SQLITE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ReviewProperties properties;
    private final JdbcClient jdbc;
    private final ReviewImageStore imageStore;
    private final ReviewFeedbackWriter feedbackWriter;
    private final TransactionTemplate transaction;
    private final Path root;
    private final ChangeListeners listeners = new ChangeListeners("Review");

    @Autowired
    public ReviewService(JdbcClient jdbc, ReviewImageStore imageStore, ReviewFeedbackWriter feedbackWriter, TransactionTemplate transaction,
                         ReviewProperties properties) {
        this(jdbc, imageStore, feedbackWriter, transaction, properties, DEFAULT_ROOT);
    }

    ReviewService(JdbcClient jdbc, ReviewImageStore imageStore, ReviewFeedbackWriter feedbackWriter, TransactionTemplate transaction,
                  ReviewProperties properties, Path root) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.imageStore = imageStore;
        this.feedbackWriter = feedbackWriter;
        this.transaction = transaction;
        this.root = root;
    }

    /** @throws IllegalArgumentException for a blank title or images the store rejects */
    public Review submit(long projectId, String ccmSessionId, String title, String note, List<SubmittedImage> images) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("The review needs a title.");
        }
        var id = transaction.execute(status -> {
            if (jdbc.sql("SELECT COUNT(*) FROM review WHERE project_id = ?").param(projectId).query(Integer.class).single() >= properties.maxReviewsPerProject()) {
                throw new IllegalArgumentException("too many reviews are waiting in this project: ask the user to delete some in the Review tab");
            }
            return jdbc.sql("INSERT INTO review (project_id, ccm_session_id, title, note) VALUES (?, ?, ?, ?) RETURNING id")
                    .param(projectId).param(ccmSessionId).param(title.strip()).param(note == null ? "" : note).query(Long.class).single();
        });
        try {
            insertImages(id, imageStore.store(directoryOf(id), images));
        } catch (RuntimeException e) {
            try {
                removeQuietly(id);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        listeners.fire();
        return find(id).orElseThrow();
    }

    public List<Review> list(long projectId) {
        return jdbc.sql(SELECT + " WHERE project_id = ? ORDER BY id DESC").param(projectId).query(ReviewService::mapReview).list();
    }

    public Optional<Review> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(ReviewService::mapReview).optional();
    }

    public List<ReviewImage> images(long reviewId) {
        return jdbc.sql("SELECT id, review_id, position, label, file_name, width, height FROM review_image WHERE review_id = ? ORDER BY position")
                .param(reviewId).query(ReviewService::mapImage).list();
    }

    public Path imagePath(ReviewImage image) {
        return directoryOf(image.reviewId()).resolve(image.fileName());
    }

    public Path directoryOf(long reviewId) {
        return root.resolve(Long.toString(reviewId));
    }

    public Path rootDirectory() {
        return root;
    }

    public List<ReviewMark> marks(long imageId) {
        return jdbc.sql("SELECT id, image_id, kind, x, y, w, h, comment, points FROM review_mark WHERE image_id = ? ORDER BY id")
                .param(imageId).query(ReviewService::mapMark).list();
    }

    /** Replaces all marks of the image; coordinates are clamped to the 0..1 image box. */
    public void saveMarks(long imageId, List<MarkDraft> marks) {
        var normalised = marks.stream().map(ReviewService::normalised).toList();
        transaction.executeWithoutResult(status -> {
            if (jdbc.sql("SELECT COUNT(*) FROM review_image WHERE id = ?").param(imageId).query(Integer.class).single() == 0) {
                throw new IllegalArgumentException("the review was deleted");
            }
            jdbc.sql("DELETE FROM review_mark WHERE image_id = ?").param(imageId).update();
            for (var mark : normalised) {
                jdbc.sql("INSERT INTO review_mark (image_id, kind, x, y, w, h, comment, points) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                        .param(imageId).param(mark.kind().name()).param(mark.x()).param(mark.y())
                        .param(mark.w()).param(mark.h()).param(mark.comment()).param(pointsJson(mark.points())).update();
            }
        });
        listeners.fire();
    }

    /** Clamps to the image box; a PIN has no size and a FREEFORM's box is recomputed from its points. */
    private static MarkDraft normalised(MarkDraft mark) {
        return switch (mark.kind()) {
            case PIN -> new MarkDraft(mark.kind(), unit(mark.x()), unit(mark.y()), 0.0, 0.0, mark.comment());
            case FREEFORM -> freeform(mark);
            default -> new MarkDraft(mark.kind(), unit(mark.x()), unit(mark.y()), unit(mark.w()), unit(mark.h()), mark.comment());
        };
    }

    private static MarkDraft freeform(MarkDraft mark) {
        if (mark.points().size() < MIN_FREEFORM_POINTS) {
            throw new IllegalArgumentException("a freeform mark needs at least " + MIN_FREEFORM_POINTS + " points");
        }
        var points = mark.points().stream().map(p -> new MarkPoint(unit(p.x()), unit(p.y()))).toList();
        var minX = points.stream().mapToDouble(MarkPoint::x).min().orElseThrow();
        var minY = points.stream().mapToDouble(MarkPoint::y).min().orElseThrow();
        var maxX = points.stream().mapToDouble(MarkPoint::x).max().orElseThrow();
        var maxY = points.stream().mapToDouble(MarkPoint::y).max().orElseThrow();
        return new MarkDraft(mark.kind(), minX, minY, maxX - minX, maxY - minY, mark.comment(), points);
    }

    private static String pointsJson(List<MarkPoint> points) {
        return JSON.writeValueAsString(points.stream().map(p -> new double[] {p.x(), p.y()}).toList());
    }

    private record ParsedPoints(List<MarkPoint> points, boolean repaired) {
    }

    /** Never throws: unusable pairs are dropped and out-of-range values clamped, so one bad row cannot hide the marks of an image. */
    private static ParsedPoints parsePoints(String json) {
        var points = new ArrayList<MarkPoint>();
        var repaired = false;
        try {
            var root = JSON.readTree(json == null ? "[]" : json);
            if (!root.isArray()) {
                return new ParsedPoints(List.of(), true);
            }
            for (var pair : root) {
                if (pair.isArray() && pair.size() >= 2 && pair.get(0).isNumber() && pair.get(1).isNumber()
                        && Double.isFinite(pair.get(0).doubleValue()) && Double.isFinite(pair.get(1).doubleValue())) {
                    var point = new MarkPoint(unit(pair.get(0).doubleValue()), unit(pair.get(1).doubleValue()));
                    repaired |= point.x() != pair.get(0).doubleValue() || point.y() != pair.get(1).doubleValue();
                    points.add(point);
                } else {
                    repaired = true;
                }
            }
        } catch (RuntimeException e) {
            return new ParsedPoints(List.of(), true);
        }
        return new ParsedPoints(List.copyOf(points), repaired);
    }

    private static List<MarkPoint> boundingBoxPolygon(double x, double y, double w, double h) {
        var x1 = unit(x + w);
        var y1 = unit(y + h);
        var x0 = unit(x);
        var y0 = unit(y);
        return List.of(new MarkPoint(x0, y0), new MarkPoint(x1, y0), new MarkPoint(x1, y1), new MarkPoint(x0, y1));
    }

    /** Writes the feedback files, marks the review as sent and returns the prompt to type into the session. */
    public ReviewFeedback prepareFeedback(long reviewId) {
        var review = find(reviewId).orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));
        var images = images(reviewId);
        var marksByImageId = new HashMap<Long, List<ReviewMark>>();
        images.forEach(image -> marksByImageId.put(image.id(), marks(image.id())));
        var feedback = feedbackWriter.write(review, images, Map.copyOf(marksByImageId), directoryOf(reviewId));
        settle(reviewId, ReviewStatus.SENT);
        return feedback;
    }

    public void approve(long reviewId) {
        settle(reviewId, ReviewStatus.APPROVED);
    }

    public void delete(long reviewId) {
        if (jdbc.sql("DELETE FROM review WHERE id = ?").param(reviewId).update() > 0) {
            deleteFolder(directoryOf(reviewId));
            listeners.fire();
        }
    }

    /** @return how many reviews were removed */
    public int deleteOlderThan(Instant cutoff) {
        var ids = jdbc.sql("SELECT id FROM review WHERE created_at < ?").param(SQLITE_TIMESTAMP.format(cutoff)).query(Long.class).list();
        ids.forEach(id -> {
            jdbc.sql("DELETE FROM review WHERE id = ?").param(id).update();
            deleteFolder(directoryOf(id));
        });
        if (!ids.isEmpty()) {
            listeners.fire();
        }
        return ids.size();
    }

    public Set<Long> knownIds() {
        return new HashSet<>(jdbc.sql("SELECT id FROM review").query(Long.class).list());
    }

    /** @return unsubscribe handle; callback fires (any thread) after any change made through this service */
    public Runnable subscribe(Runnable listener) {
        return listeners.add(listener);
    }

    private void insertImages(long reviewId, List<StoredImage> stored) {
        for (var position = 0; position < stored.size(); position++) {
            var image = stored.get(position);
            jdbc.sql("INSERT INTO review_image (review_id, position, label, file_name, width, height) VALUES (?, ?, ?, ?, ?, ?)")
                    .param(reviewId).param(position).param(image.label()).param(image.fileName()).param(image.width()).param(image.height()).update();
        }
    }

    private void settle(long reviewId, ReviewStatus status) {
        jdbc.sql("UPDATE review SET status = ?, settled_at = CURRENT_TIMESTAMP WHERE id = ?").param(status.name()).param(reviewId).update();
        listeners.fire();
    }

    private void removeQuietly(long id) {
        jdbc.sql("DELETE FROM review WHERE id = ?").param(id).update();
        deleteFolder(directoryOf(id));
    }

    private static void deleteFolder(Path folder) {
        try {
            if (java.nio.file.Files.exists(folder)) {
                TreeDeleter.delete(folder);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double unit(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    private static Review mapReview(ResultSet rs, int row) throws SQLException {
        return new Review(rs.getLong("id"), rs.getLong("project_id"), rs.getString("ccm_session_id"), rs.getString("title"), rs.getString("note"),
                ReviewStatus.valueOf(rs.getString("status")), instant(rs.getString("created_at")), instant(rs.getString("settled_at")));
    }

    private static ReviewImage mapImage(ResultSet rs, int row) throws SQLException {
        return new ReviewImage(rs.getLong("id"), rs.getLong("review_id"), rs.getInt("position"), rs.getString("label"), rs.getString("file_name"),
                rs.getInt("width"), rs.getInt("height"));
    }

    private static ReviewMark mapMark(ResultSet rs, int row) throws SQLException {
        var kind = MarkKind.valueOf(rs.getString("kind"));
        var x = rs.getDouble("x");
        var y = rs.getDouble("y");
        var w = rs.getDouble("w");
        var h = rs.getDouble("h");
        var parsed = parsePoints(rs.getString("points"));
        var points = parsed.points();
        var repaired = parsed.repaired();
        if (kind == MarkKind.FREEFORM && points.size() < MIN_FREEFORM_POINTS) {
            points = boundingBoxPolygon(x, y, w, h);
            repaired = true;
        }
        if (repaired) {
            LOG.warn("A stored review mark had unusable points and was repaired.");
        }
        return new ReviewMark(rs.getLong("id"), rs.getLong("image_id"), kind, x, y, w, h, rs.getString("comment"), points);
    }

    private static Instant instant(String sqliteTimestamp) {
        return sqliteTimestamp == null ? null : LocalDateTime.parse(sqliteTimestamp, SQLITE_TIMESTAMP).toInstant(ZoneOffset.UTC);
    }

}
