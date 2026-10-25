package com.github.krpkbhdn.ccmanager.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UpdateServiceTest {

    private static final byte[] INSTALLER = "installer-bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dataDir;

    private HttpServer server;
    private String checksum;
    private UpdateService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/app.msi", exchange -> respond(exchange, INSTALLER));
        server.createContext("/app.msi.sha256", exchange -> respond(exchange, (checksum + "  app.msi\n").getBytes(StandardCharsets.UTF_8)));
        server.start();
        var properties = new UpdateProperties("1.0.0", "o/r", "http://unused", 6, 5);
        service = new UpdateService(properties, new AppProperties(dataDir), JsonMapper.builder().build(), mock(UpdateInstaller.class));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void wrongChecksumFailsAndLeavesNoInstaller() {
        checksum = "0".repeat(64);

        assertThatThrownBy(() -> service.download(update(), progress -> { })).isInstanceOf(IllegalStateException.class).hasMessageContaining("checksum");
        assertThat(dataDir.resolve("updates")).isEmptyDirectory();
    }

    @Test
    void rightChecksumMovesTheInstallerAndRemovesOlderOnes() throws Exception {
        checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(INSTALLER));
        var updates = Files.createDirectories(dataDir.resolve("updates"));
        Files.writeString(updates.resolve("old-0.9.0.msi"), "old");

        var installer = service.download(update(), progress -> { });

        assertThat(installer).isEqualTo(updates.resolve("app.msi")).hasBinaryContent(INSTALLER);
        assertThat(updates).isDirectoryContaining(path -> path.equals(installer)).isDirectoryNotContaining(path -> path.getFileName().toString().startsWith("old"));
    }

    private AvailableUpdate update() {
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AvailableUpdate("1.0.1", "app.msi", URI.create(base + "/app.msi"), URI.create(base + "/app.msi.sha256"), URI.create(base));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
