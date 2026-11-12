package com.github.krpkbhdn.ccmanager.web;

import com.github.krpkbhdn.ccmanager.review.ReviewStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** All SQL of the web review and the row mapping; callers own the transactions. A stored item that cannot be read is skipped and logged. */
class WebReviewRepository {

    /** The columns of an item row needed to act on it, without its JSON (which may be corrupt). */
    record ItemRef(long id, long revisionId, long projectId, int n, WebItemStatus status) {
    }

    /** A page row after an upsert; {@code changed} is true for a new page or one whose URL or title differ. */
    record PageUpsert(long id, boolean changed) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(WebReviewRepository.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter SQLITE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final String NOW = "strftime('%Y-%m-%d %H:%M:%f', 'now')";
    private static final String REVISION_SELECT =
            "SELECT id, project_id, number, ccm_session_id, note, status, created_at, sent_at, settled_at FROM web_review_revision";
    private static final String ITEM_SELECT =
            "SELECT i.id, i.revision_id, i.page_id, i.n, i.anchor_json, i.comment, i.style_changes_json, i.status, i.screenshot_file, i.viewport_width, i.viewport_height, i.device_name, i.device_category FROM web_review_item i";

    private final JdbcClient jdbc;

    WebReviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<WebRevision> revisions(long projectId) {
        return jdbc.sql(REVISION_SELECT + " WHERE project_id = ? ORDER BY number DESC").param(projectId).query(WebReviewRepository::mapRevision).list();
    }

    Optional<WebRevision> findRevision(long revisionId) {
        return jdbc.sql(REVISION_SELECT + " WHERE id = ?").param(revisionId).query(WebReviewRepository::mapRevision).optional();
    }

    Optional<WebRevision> pendingRevision(long projectId) {
        return jdbc.sql(REVISION_SELECT + " WHERE project_id = ? AND status = 'PENDING'").param(projectId).query(WebReviewRepository::mapRevision)
                .optional();
    }

    /** On a lost race for the single pending slot the winner's revision is used. */
    WebRevision pendingOrNewRevision(long projectId) {
        var existing = pendingRevision(projectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return createRevision(projectId);
        } catch (DataIntegrityViolationException e) {
            return pendingRevision(projectId).orElseThrow(() -> e);
        }
    }

    WebRevision createRevision(long projectId) {
        var number = jdbc.sql("SELECT COALESCE(MAX(number), 0) + 1 FROM web_review_revision WHERE project_id = ?").param(projectId)
                .query(Integer.class).single();
        return jdbc.sql("INSERT INTO web_review_revision (project_id, number) VALUES (?, ?) RETURNING id, project_id, number, ccm_session_id, note, "
                + "status, created_at, sent_at, settled_at").param(projectId).param(number).query(WebReviewRepository::mapRevision).single();
    }

    boolean isPending(long revisionId) {
        return count("SELECT COUNT(*) FROM web_review_revision WHERE id = ? AND status = 'PENDING'", revisionId) > 0;
    }

    void markRevisionSent(long revisionId) {
        jdbc.sql("UPDATE web_review_revision SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id = ?").param(revisionId).update();
    }

    void approveRevision(long revisionId) {
        jdbc.sql("UPDATE web_review_item SET status = 'DONE', updated_at = CURRENT_TIMESTAMP WHERE revision_id = ? AND status = 'SENT'")
                .param(revisionId).update();
        jdbc.sql("UPDATE web_review_revision SET status = 'APPROVED', settled_at = CURRENT_TIMESTAMP WHERE id = ?").param(revisionId).update();
    }

    void bindSession(long revisionId, String ccmSessionId) {
        jdbc.sql("UPDATE web_review_revision SET ccm_session_id = ? WHERE id = ?").param(ccmSessionId).param(revisionId).update();
    }

    /** @return the approved revisions settled before the cutoff, as (id, project id, number) */
    List<WebRevision> approvedBefore(Instant cutoff) {
        return jdbc.sql(REVISION_SELECT + " WHERE status = 'APPROVED' AND settled_at < ?").param(SQLITE_TIMESTAMP.format(cutoff))
                .query(WebReviewRepository::mapRevision).list();
    }

    void deleteRevision(long revisionId) {
        jdbc.sql("DELETE FROM web_review_revision WHERE id = ?").param(revisionId).update();
    }

    String projectName(long projectId) {
        return jdbc.sql("SELECT name FROM project WHERE id = ?").param(projectId).query(String.class).optional().orElse(Long.toString(projectId));
    }

    List<WebPage> pages(long projectId) {
        return jdbc.sql("SELECT id, project_id, page_key, last_url, title FROM web_review_page WHERE project_id = ? ORDER BY last_visited_at, id")
                .param(projectId).query(WebReviewRepository::mapPage).list();
    }

    PageUpsert upsertPage(long projectId, String pageKey, String url, String title) {
        var before = jdbc.sql("SELECT last_url, title FROM web_review_page WHERE project_id = ? AND page_key = ?").param(projectId).param(pageKey)
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2))).optional();
        var id = jdbc.sql("INSERT INTO web_review_page (project_id, page_key, last_url, title) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (project_id, page_key) DO UPDATE SET last_url = excluded.last_url, title = excluded.title, last_visited_at = " + NOW
                + " RETURNING id").param(projectId).param(pageKey).param(url).param(title).query(Long.class).single();
        return new PageUpsert(id, before.isEmpty() || !before.get().equals(List.of(url, title)));
    }

    List<WebItem> overlayItems(long projectId, String pageKey) {
        return readable(jdbc.sql(ITEM_SELECT + " JOIN web_review_page p ON p.id = i.page_id WHERE p.project_id = ? AND p.page_key = ? "
                + "AND i.status <> 'DISMISSED' ORDER BY i.n").param(projectId).param(pageKey).query(WebReviewRepository::mapItem).list());
    }

    List<WebItem> revisionItems(long revisionId) {
        return readable(jdbc.sql(ITEM_SELECT + " WHERE i.revision_id = ? ORDER BY i.n").param(revisionId).query(WebReviewRepository::mapItem).list());
    }

    Optional<WebItem> findItem(long itemId) {
        return jdbc.sql(ITEM_SELECT + " WHERE i.id = ?").param(itemId).query(WebReviewRepository::mapItem).optional().flatMap(item -> item);
    }

    Optional<ItemRef> itemRef(long itemId) {
        return jdbc.sql("SELECT i.id, i.revision_id, r.project_id, i.n, i.status FROM web_review_item i JOIN web_review_revision r ON r.id = i.revision_id "
                + "WHERE i.id = ?").param(itemId).query((rs, row) -> new ItemRef(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4),
                WebItemStatus.valueOf(rs.getString(5)))).optional();
    }

    int itemCount(long revisionId) {
        return count("SELECT COUNT(*) FROM web_review_item WHERE revision_id = ?", revisionId);
    }

    WebItem insertItem(long projectId, long revisionId, long pageId, ElementAnchor anchor, String comment, List<StyleChange> changes,
            WebViewport viewport) {
        var n = jdbc.sql("SELECT COALESCE(MAX(i.n), 0) + 1 FROM web_review_item i JOIN web_review_revision r ON r.id = i.revision_id WHERE r.project_id = ?")
                .param(projectId).query(Integer.class).single();
        var id = jdbc.sql("INSERT INTO web_review_item (revision_id, page_id, n, anchor_json, comment, style_changes_json, viewport_width, viewport_height, device_name, device_category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")
                .param(revisionId).param(pageId).param(n).param(JSON.writeValueAsString(anchor)).param(comment)
                .param(JSON.writeValueAsString(changes)).param(viewport == null ? null : viewport.width())
                .param(viewport == null ? null : viewport.height()).param(viewport == null ? null : viewport.deviceName())
                .param(viewport == null ? null : viewport.category().name()).query(Long.class).single();
        return findItem(id).orElseThrow();
    }

    void updateContent(long itemId, String comment, List<StyleChange> changes) {
        jdbc.sql("UPDATE web_review_item SET comment = ?, style_changes_json = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .param(comment).param(JSON.writeValueAsString(changes)).param(itemId).update();
    }

    void moveAndSetStatus(long itemId, WebItemStatus status, long revisionId) {
        jdbc.sql("UPDATE web_review_item SET status = ?, revision_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .param(status.name()).param(revisionId).param(itemId).update();
    }

    void markSent(long itemId) {
        jdbc.sql("UPDATE web_review_item SET status = 'SENT', updated_at = CURRENT_TIMESTAMP WHERE id = ?").param(itemId).update();
    }

    void setScreenshot(long itemId, String file) {
        jdbc.sql("UPDATE web_review_item SET screenshot_file = ? WHERE id = ?").param(file).param(itemId).update();
    }

    boolean deleteItem(long itemId) {
        return jdbc.sql("DELETE FROM web_review_item WHERE id = ?").param(itemId).update() > 0;
    }

    private int count(String sql, long id) {
        return jdbc.sql(sql).param(id).query(Integer.class).single();
    }

    private static List<WebItem> readable(List<Optional<WebItem>> rows) {
        return rows.stream().flatMap(Optional::stream).toList();
    }

    private static WebRevision mapRevision(ResultSet rs, int row) throws SQLException {
        return new WebRevision(rs.getLong("id"), rs.getLong("project_id"), rs.getInt("number"), rs.getString("ccm_session_id"), rs.getString("note"),
                ReviewStatus.valueOf(rs.getString("status")), instant(rs.getString("created_at")), instant(rs.getString("sent_at")),
                instant(rs.getString("settled_at")));
    }

    private static WebPage mapPage(ResultSet rs, int row) throws SQLException {
        return new WebPage(rs.getLong("id"), rs.getLong("project_id"), rs.getString("page_key"), rs.getString("last_url"), rs.getString("title"));
    }

    private static Optional<WebItem> mapItem(ResultSet rs, int row) throws SQLException {
        var id = rs.getLong("id");
        try {
            return Optional.of(new WebItem(id, rs.getLong("revision_id"), rs.getLong("page_id"), rs.getInt("n"),
                    JSON.readValue(rs.getString("anchor_json"), ElementAnchor.class), rs.getString("comment"),
                    JSON.readValue(rs.getString("style_changes_json"), new TypeReference<List<StyleChange>>() { }),
                    WebItemStatus.valueOf(rs.getString("status")), rs.getString("screenshot_file"), viewportOf(rs)));
        } catch (RuntimeException e) {
            LOG.warn("A stored web review item (id {}) could not be read and was skipped.", id);
            return Optional.empty();
        }
    }

    /** An unreadable stored viewport counts as none, so the item stays visible everywhere. */
    private static WebViewport viewportOf(ResultSet rs) throws SQLException {
        var category = rs.getString("device_category");
        if (category == null) {
            return null;
        }
        try {
            return new WebViewport(rs.getInt("viewport_width"), rs.getInt("viewport_height"), rs.getString("device_name"), DeviceCategory.valueOf(category));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant instant(String sqliteTimestamp) {
        return sqliteTimestamp == null ? null : LocalDateTime.parse(sqliteTimestamp, SQLITE_TIMESTAMP).toInstant(ZoneOffset.UTC);
    }
}
