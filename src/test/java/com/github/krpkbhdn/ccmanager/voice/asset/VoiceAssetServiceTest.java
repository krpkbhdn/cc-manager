package com.github.krpkbhdn.ccmanager.voice.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.download.VerifiedDownload;
import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoiceAssetServiceTest {

    private static final byte[] MODEL = "model".repeat(100).getBytes(StandardCharsets.UTF_8);
    private static final byte[] RUNTIME_ZIP = zip("Release/whisper.dll", "exe");
    private static final byte[] EVIL_ZIP = zip("../escaped.txt", "x");

    @TempDir
    Path dataDir;

    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private VoiceAssetService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serve("/model", MODEL);
        serve("/runtime.zip", RUNTIME_ZIP);
        serve("/evil.zip", EVIL_ZIP);
        server.start();
        var properties = new VoiceProperties("auto", Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofSeconds(1),
                0.01, 0.5, 4, Duration.ofSeconds(5), Duration.ofMinutes(5));
        service = new VoiceAssetService(new AppProperties(dataDir), new VerifiedDownload(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void installsPlainFileAndReportsItsPath() throws Exception {
        var asset = asset("/model", MODEL, "models/m.bin", false);
        assertThat(service.installed(asset)).isFalse();
        assertThat(service.pathOf(asset)).isEmpty();

        var path = service.install(asset, progress -> { });

        assertThat(path).isEqualTo(dataDir.resolve("voice/models/m.bin")).hasBinaryContent(MODEL);
        assertThat(service.installed(asset)).isTrue();
        assertThat(service.pathOf(asset)).contains(path);
    }

    @Test
    void unpacksArchiveIntoDirectoryAndDeletesZip() throws Exception {
        var asset = asset("/runtime.zip", RUNTIME_ZIP, "runtime/cpu", true);

        var path = service.install(asset, progress -> { });

        assertThat(path.resolve("Release/whisper.dll")).hasContent("exe");
        assertThat(names(dataDir.resolve("voice"))).noneMatch(name -> name.endsWith(".zip") || name.endsWith(".part"));
    }

    @Test
    void secondInstallDoesNotTouchTheNetwork() throws Exception {
        var asset = asset("/model", MODEL, "models/m.bin", false);
        service.install(asset, progress -> { });

        service.install(asset, progress -> { });

        assertThat(requests).hasValue(1);
    }

    @Test
    void parallelInstallsOfTheSameAssetDownloadItOnce() throws Exception {
        var asset = asset("/runtime.zip", RUNTIME_ZIP, "runtime/cpu", true);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Path>>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> service.install(asset, progress -> { })));
            }
            for (var result : results) {
                assertThat(result.get()).isEqualTo(dataDir.resolve("voice/runtime/cpu"));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(requests).hasValue(1);
        assertThat(names(dataDir.resolve("voice"))).noneMatch(name -> name.endsWith(".zip") || name.endsWith(".part"));
    }

    @Test
    void checksumMismatchLeavesNothingBehind() {
        var asset = new VoiceAsset("m", url("/model"), "00".repeat(32), MODEL.length, "models/m.bin", false);

        assertThatThrownBy(() -> service.install(asset, progress -> { })).isInstanceOf(IllegalStateException.class);

        assertThat(service.installed(asset)).isFalse();
        assertThat(names(dataDir.resolve("voice"))).noneMatch(name -> name.endsWith(".part"));
    }

    @Test
    void zipSlipEntryIsRejectedAndNothingIsInstalled() throws Exception {
        var asset = asset("/evil.zip", EVIL_ZIP, "runtime/evil", true);

        assertThatThrownBy(() -> service.install(asset, progress -> { })).isInstanceOf(IOException.class);

        assertThat(service.installed(asset)).isFalse();
        assertThat(dataDir.resolve("voice/runtime/escaped.txt")).doesNotExist();
        assertThat(dataDir.resolve("voice/escaped.txt")).doesNotExist();
        assertThat(names(dataDir.resolve("voice"))).noneMatch(name -> name.endsWith(".part") || name.endsWith(".zip"));
    }

    @Test
    void removeDeletesFileAndDirectory() throws Exception {
        var file = asset("/model", MODEL, "models/m.bin", false);
        var archive = asset("/runtime.zip", RUNTIME_ZIP, "runtime/cpu", true);
        service.install(file, progress -> { });
        service.install(archive, progress -> { });

        service.remove(file);
        service.remove(archive);

        assertThat(service.installed(file)).isFalse();
        assertThat(service.installed(archive)).isFalse();
    }

    @Test
    void removeLeavesNoRemovingDirectoryAndStaleDownloadsAreCleanedOnInstall() throws Exception {
        var archive = asset("/runtime.zip", RUNTIME_ZIP, "runtime/cpu", true);
        service.install(archive, progress -> { });
        Files.createDirectories(dataDir.resolve("voice/runtime/cpu.removing"));
        Files.writeString(dataDir.resolve("voice/download-1.zip.part"), "junk");

        service.remove(archive);
        service.install(asset("/model", MODEL, "models/m.bin", false), progress -> { });

        assertThat(dataDir.resolve("voice/runtime/cpu.removing")).doesNotExist();
        assertThat(dataDir.resolve("voice/download-1.zip.part")).doesNotExist();
    }

    @Test
    void wrongSizeIsRejectedAndTheFileDeleted() throws Exception {
        var asset = new VoiceAsset("m", url("/model"), sha256(MODEL), MODEL.length + 1, "models/m.bin", false);

        assertThatThrownBy(() -> service.install(asset, progress -> { })).isInstanceOf(IOException.class).hasMessageContaining("size");

        assertThat(service.installed(asset)).isFalse();
    }

    @Test
    void hostStreamingMoreThanDeclaredIsAbortedAndNothingKept() {
        var asset = new VoiceAsset("m", url("/model"), sha256Of(MODEL), MODEL.length - 1, "models/m.bin", false);

        assertThatThrownBy(() -> service.install(asset, progress -> { })).isInstanceOf(IOException.class).hasMessageContaining("larger");

        assertThat(service.installed(asset)).isFalse();
        assertThat(names(dataDir.resolve("voice"))).noneMatch(name -> name.endsWith(".part"));
    }

    private static String sha256Of(byte[] content) {
        try {
            return sha256(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private VoiceAsset asset(String path, byte[] content, String relativePath, boolean archive) throws Exception {
        return new VoiceAsset("test", url(path), sha256(content), content.length, relativePath, archive);
    }

    private void serve(String path, byte[] content) {
        server.createContext(path, exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, content.length);
            try (var out = exchange.getResponseBody()) {
                out.write(content);
            }
        });
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static byte[] zip(String name, String content) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static List<String> names(Path directory) {
        try (var files = Files.walk(directory)) {
            return files.map(path -> path.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
