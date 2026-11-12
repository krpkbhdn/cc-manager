package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.review.MarkKind;
import com.github.krpkbhdn.ccmanager.review.MarkPoint;
import com.github.krpkbhdn.ccmanager.review.ReviewStatus;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class WebShapeItemTest {

    private static final String URL = "http://localhost:5173/a";
    private static final WebViewport DESKTOP = new WebViewport(1280, 800, null, DeviceCategory.DESKTOP);

    @TempDir
    Path temp;

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;
    WebReviewService service;
    WebReviewProperties properties = new WebReviewProperties(600, List.of("display"), 1024, 50);
    long projectId;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("test.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        service = new WebReviewService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), properties,
                new WebScreenshotStore(new AppProperties(temp), properties), new WebFeedbackWriter(properties));
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.resolve("app").toString()).update();
        projectId = jdbc.sql("SELECT id FROM project WHERE name = 'app'").query(Long.class).single();
    }

    private WebItem addShape(WebMarkShape shape) {
        return service.addItem(projectId, URL, "A", null, "fix this", List.of(), DESKTOP, shape);
    }

    private static byte[] whitePng(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void shapeItemRoundTripsWithRectOnlyAnchor() {
        var shape = new WebMarkShape(MarkKind.RECTANGLE, 10.5, 20, 100, 50);

        var item = addShape(shape);

        assertThat(item.shape()).isEqualTo(shape);
        assertThat(item.anchor().selector()).isEmpty();
        assertThat(item.anchor().rect()).isEqualTo(new ElementAnchor.Rect(10.5, 20, 100, 50));
        assertThat(service.allItems(item.revisionId())).singleElement().extracting(WebItem::shape).isEqualTo(shape);
        assertThat(service.items(projectId, PageKey.of(URL))).singleElement().extracting(WebItem::shape).isEqualTo(shape);
    }

    @Test
    void updateItemShapeReplacesTheShapeOfAnOpenDrawnItemOnly() {
        var item = addShape(new WebMarkShape(MarkKind.ELLIPSE, 1, 1, 5, 5));
        var moved = new WebMarkShape(MarkKind.ELLIPSE, 50, 60, 5, 5);

        service.updateItemShape(item.id(), moved);

        assertThat(service.allItems(item.revisionId()).getFirst().shape()).isEqualTo(moved);
        var picked = service.addItem(projectId, URL, "A", new ElementAnchor("p", null, null, null, null, null, null, null, null), "c", List.of());
        assertThatThrownBy(() -> service.updateItemShape(picked.id(), moved)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateItemShape(9999, moved)).isInstanceOf(IllegalArgumentException.class);
        service.setStatus(item.id(), WebItemStatus.DISMISSED);
        assertThatThrownBy(() -> service.updateItemShape(item.id(), moved)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void annotatedScreenshotBurnsTheMarkInAndFollowsTheItem() throws IOException {
        var item = addShape(new WebMarkShape(MarkKind.RECTANGLE, 10, 10, 40, 30));

        service.saveAnnotatedScreenshot(item.id(), whitePng(100, 80), new WebMarkShape(MarkKind.RECTANGLE, 10, 10, 40, 30));

        var stored = service.allItems(item.revisionId()).getFirst();
        assertThat(stored.screenshotFile()).isEqualTo("item-1.png");
        var file = new WebScreenshotStore(new AppProperties(temp), properties).directory(projectId, item.revisionId()).resolve("item-1.png");
        var image = ImageIO.read(file.toFile());
        assertThat(image.getWidth()).isEqualTo(100);
        assertThat(new Color(image.getRGB(50, 25), true)).as("on the right edge of the rectangle").isNotEqualTo(Color.WHITE);
        assertThat(new Color(image.getRGB(90, 70), true)).as("outside the mark").isEqualTo(Color.WHITE);

        service.deleteItem(item.id());
        assertThat(file).doesNotExist();
    }

    @Test
    void approvedRevisionCleanupRemovesTheAnnotatedScreenshot() throws IOException {
        var item = addShape(new WebMarkShape(MarkKind.PIN, 5, 5, 0, 0));
        service.saveAnnotatedScreenshot(item.id(), whitePng(50, 50), new WebMarkShape(MarkKind.PIN, 5, 5, 0, 0));
        var directory = new WebScreenshotStore(new AppProperties(temp), properties).directory(projectId, item.revisionId());
        service.prepareFeedback(item.revisionId());
        service.approve(item.revisionId());
        jdbc.sql("UPDATE web_review_revision SET settled_at = '2000-01-01 00:00:00' WHERE id = ?").param(item.revisionId()).update();

        assertThat(service.deleteApprovedBefore(Instant.now())).isEqualTo(1);

        assertThat(directory).doesNotExist();
    }

    @Test
    void writerDescribesADrawnItemWithoutSelectorBlock() throws IOException {
        var shape = new WebMarkShape(MarkKind.FREEFORM, 1, 2, 30.25, 40,
                List.of(new MarkPoint(1, 2), new MarkPoint(31, 2), new MarkPoint(15, 42)));
        var item = addShape(shape);
        var revision = new WebRevision(9, 1, 3, null, "", ReviewStatus.PENDING, Instant.now(), null, null);
        var page = new WebPage(item.pageId(), projectId, "k", URL, "A");
        var withShot = new WebItem(item.id(), item.revisionId(), item.pageId(), 1, item.anchor(), "fix this", List.of(), WebItemStatus.OPEN,
                "item-1.png", DESKTOP, shape);

        var feedback = new WebFeedbackWriter(properties).write("shop", revision, List.of(page), List.of(withShot), temp.resolve("web-review/1/9"));

        var md = Files.readString(feedback.markdown());
        assertThat(md).contains("### 1\n", "shape: `drawn freeform at 1,2 30.3 x 40 (document px, device 1280 x 800)`",
                "vertices: `1,2; 31,2; 15,42`", "comment: `fix this`", "shot: `item-1.png`").doesNotContain("###  ");
    }

    @Test
    void movingAShapeDropsTheStaleScreenshotFileAndColumn() throws IOException {
        var item = addShape(new WebMarkShape(MarkKind.RECTANGLE, 10, 10, 40, 30));
        service.saveAnnotatedScreenshot(item.id(), whitePng(100, 80), new WebMarkShape(MarkKind.RECTANGLE, 10, 10, 40, 30));
        var directory = new WebScreenshotStore(new AppProperties(temp), properties).directory(projectId, item.revisionId());
        assertThat(directory.resolve("item-1.png")).exists();
        assertThat(directory.resolve("item-1.png.tmp")).doesNotExist();

        assertThat(service.tryUpdateItemShape(item.id(), new WebMarkShape(MarkKind.RECTANGLE, 50, 50, 40, 30))).isTrue();

        assertThat(directory.resolve("item-1.png")).doesNotExist();
        assertThat(service.allItems(item.revisionId()).getFirst().screenshotFile()).isNull();
    }

    @Test
    void tryUpdateItemShapeReturnsFalseForAVanishedOrNotOpenItem() {
        var item = addShape(new WebMarkShape(MarkKind.ELLIPSE, 1, 1, 5, 5));
        var moved = new WebMarkShape(MarkKind.ELLIPSE, 50, 60, 5, 5);
        assertThat(service.tryUpdateItemShape(9999, moved)).isFalse();
        service.setStatus(item.id(), WebItemStatus.DISMISSED);
        assertThat(service.tryUpdateItemShape(item.id(), moved)).isFalse();
        service.deleteItem(item.id());
        assertThat(service.tryUpdateItemShape(item.id(), moved)).isFalse();
    }

    @Test
    void anItemWithAnUnreadableShapeIsSkippedNotFatal() {
        var item = addShape(new WebMarkShape(MarkKind.ELLIPSE, 1, 1, 5, 5));
        jdbc.sql("UPDATE web_review_item SET shape_json = 'garbage' WHERE id = ?").param(item.id()).update();

        assertThat(service.allItems(item.revisionId())).isEmpty();
    }

    @Test
    void writerKeepsAllVerticesOfALargePolygonButCapsTheLine() throws IOException {
        var points = new java.util.ArrayList<MarkPoint>();
        for (int i = 0; i < 500; i++) {
            points.add(new MarkPoint(1000 + i, 2000 + i));
        }
        var shape = new WebMarkShape(MarkKind.FREEFORM, 0, 0, 10, 10, points);
        var item = new WebItem(1, 9, 5, 1, new ElementAnchor("", null, null, null, null, null, null, null, null), "c", List.of(),
                WebItemStatus.OPEN, null, null, shape);
        var revision = new WebRevision(9, 1, 3, null, "", ReviewStatus.PENDING, Instant.now(), null, null);
        var page = new WebPage(5, 1, "k", URL, "A");

        var md = Files.readString(new WebFeedbackWriter(properties).write("shop", revision, List.of(page), List.of(item), temp.resolve("w/1/9")).markdown());

        assertThat(md).contains("1499,2499`").doesNotContain("[truncated]");
    }

    @Test
    void v24RowSurvivesV25WithNullShape() {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("m.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(ds).target("24").load().migrate();
        var db = JdbcClient.create(ds);
        db.sql("INSERT INTO project (id, name, path) VALUES (1, 'a', '/a')").update();
        db.sql("INSERT INTO web_review_revision (id, project_id, number) VALUES (1, 1, 1)").update();
        db.sql("INSERT INTO web_review_page (id, project_id, page_key, last_url) VALUES (1, 1, 'k', 'http://x/')").update();
        db.sql("INSERT INTO web_review_item (id, revision_id, page_id, n, anchor_json, comment) VALUES (7, 1, 1, 1, '{}', 'old')").update();

        Flyway.configure().dataSource(ds).load().migrate();

        assertThat(db.sql("SELECT comment, shape_json FROM web_review_item WHERE id = 7")
                .query((rs, i) -> new Object[] {rs.getString(1), rs.getObject(2)}).single()).containsExactly("old", null);
    }
}
