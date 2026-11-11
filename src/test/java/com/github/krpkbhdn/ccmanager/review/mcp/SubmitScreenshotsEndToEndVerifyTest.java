package com.github.krpkbhdn.ccmanager.review.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpEndpoint;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.review.ReviewFeedbackWriter;
import com.github.krpkbhdn.ccmanager.review.ReviewImageAnnotator;
import com.github.krpkbhdn.ccmanager.review.ReviewImageStore;
import com.github.krpkbhdn.ccmanager.review.ReviewProperties;
import com.github.krpkbhdn.ccmanager.review.ReviewService;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class SubmitScreenshotsEndToEndVerifyTest {

    @TempDir
    Path temp;

    @Test
    void endpointPassesCallingSessionIdAndProjectToStoredReview() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("t.db") + "?foreign_keys=on");
        Flyway.configure().dataSource(ds).load().migrate();
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("INSERT INTO project (name, path, created_at) VALUES ('app', ?, 'now')").param(temp.toString()).update();
        long pid = jdbc.sql("SELECT id FROM project").query(Long.class).single();
        var props = new ReviewProperties(Duration.ofDays(7), Duration.ofHours(1), 20, 20971520L, 8192, 36_000_000L, 50);
        var service = new ReviewService(jdbc, new ReviewImageStore(props), new ReviewFeedbackWriter(new ReviewImageAnnotator()),
                new TransactionTemplate(new DataSourceTransactionManager(ds)), props);
        var snap = mock(SessionSnapshot.class);
        when(snap.projectId()).thenReturn(pid);
        var sessions = mock(SessionRegistry.class);
        when(sessions.find("sess-42")).thenReturn(Optional.of(snap));
        var mapper = JsonMapper.builder().build();
        var endpoint = new McpEndpoint(List.of(new SubmitScreenshotsTool(service)), sessions, mapper);
        var file = temp.resolve("c.png");
        ImageIO.write(new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        var body = mapper.writeValueAsString(java.util.Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params",
                java.util.Map.of("name", "submit_screenshots", "arguments",
                        java.util.Map.of("title", "T", "images", List.of(java.util.Map.of("path", file.toString()))))));

        var resp = mapper.readTree(endpoint.handle("sess-42", body.getBytes(StandardCharsets.UTF_8)).orElseThrow());

        try {
            assertThat(resp.path("result").path("isError").asBoolean(false)).isFalse();
            var review = service.list(pid).get(0);
            assertThat(review.ccmSessionId()).isEqualTo("sess-42");
            assertThat(review.projectId()).isEqualTo(pid);
            assertThat(service.images(review.id())).hasSize(1);
        } finally {
            service.list(pid).forEach(r -> service.delete(r.id()));
        }
    }
}
