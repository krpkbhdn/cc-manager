package com.github.krpkbhdn.ccmanager.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifiedDownloadTest {

    private static final byte[] CONTENT = "model-bytes".repeat(1000).getBytes(StandardCharsets.UTF_8);
    private static final Duration STALL = Duration.ofSeconds(60);

    @TempDir
    Path directory;

    private final VerifiedDownload download = new VerifiedDownload();
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(200, CONTENT.length);
            try (var out = exchange.getResponseBody()) {
                out.write(CONTENT);
            }
        });
        server.createContext("/stalls", this::sendHalfThenHang);
        server.start();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void movesVerifiedFileIntoPlaceIgnoringHashCase() throws Exception {
        var target = directory.resolve("model.bin");

        var result = download.fetch(url("/file"), sha256(CONTENT).toUpperCase(), target, STALL, progress -> { });

        assertThat(result).isEqualTo(target).hasBinaryContent(CONTENT);
        assertThat(directory.resolve("model.bin.part")).doesNotExist();
    }

    @Test
    void checksumMismatchDeletesThePartialFileAndLeavesNoTarget() {
        var target = directory.resolve("model.bin");

        assertThatThrownBy(() -> download.fetch(url("/file"), "0".repeat(64), target, STALL, progress -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("checksum");
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void reportsProgressUpToOne() throws Exception {
        List<Double> seen = new ArrayList<>();

        download.fetch(url("/file"), sha256(CONTENT), directory.resolve("model.bin"), STALL, seen::add);

        assertThat(seen).isNotEmpty().isSorted().last().isEqualTo(1.0);
    }

    @Test
    void stalledDownloadFailsAndLeavesNoPartialFile() {
        assertThatThrownBy(() -> download.fetch(url("/stalls"), sha256(CONTENT), directory.resolve("model.bin"), Duration.ofMillis(300), progress -> { }))
                .isInstanceOf(IOException.class);
        assertThat(directory).isEmptyDirectory();
    }

    private void sendHalfThenHang(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, CONTENT.length);
        var out = exchange.getResponseBody();
        out.write(CONTENT, 0, CONTENT.length / 2);
        out.flush();
        try {
            release.await(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        exchange.close();
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
