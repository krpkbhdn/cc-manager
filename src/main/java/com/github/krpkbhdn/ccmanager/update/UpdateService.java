package com.github.krpkbhdn.ccmanager.update;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Looks for a newer GitHub release, downloads its MSI with SHA-256 verification and hands it to the installer. */
@Service
@EnableConfigurationProperties(UpdateProperties.class)
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);
    private static final String UPDATES_DIRECTORY = "updates";
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final UpdateProperties properties;
    private final AppProperties app;
    private final JsonMapper mapper;
    private final UpdateInstaller installer;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    UpdateService(UpdateProperties properties, AppProperties app, JsonMapper mapper, UpdateInstaller installer) {
        this.properties = properties;
        this.app = app;
        this.mapper = mapper;
        this.installer = installer;
    }

    public boolean enabled() {
        return properties.enabled() && ReleaseVersion.parse(properties.version()).isPresent();
    }

    public Duration checkInterval() {
        return Duration.ofHours(properties.checkIntervalHours());
    }

    /** False where the app cannot replace itself; the UI then only points to the release page. */
    public boolean canInstall() {
        return installer.supported();
    }

    /** Empty when up to date, offline, or the latest release has no installable asset; failures are logged, not thrown. */
    public Optional<AvailableUpdate> findUpdate() {
        if (!enabled()) {
            return Optional.empty();
        }
        var current = ReleaseVersion.parse(properties.version()).orElseThrow();
        var latest = URI.create(properties.apiBaseUrl() + "/repos/" + properties.repository() + "/releases/latest");
        try {
            var response = get(latest, "application/vnd.github+json");
            try (var body = response.body()) {
                return ReleaseParser.newerThan(mapper.readTree(body), current);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Update check failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /** @return the verified MSI; [progress] receives 0..1 while downloading (not called when the size is unknown) */
    public Path download(AvailableUpdate update, DoubleConsumer progress) {
        try {
            var expected = expectedChecksum(update);
            var directory = Files.createDirectories(app.dataDir().resolve(UPDATES_DIRECTORY));
            deleteOldDownloads(directory);
            var target = directory.resolve(Path.of(update.installerName()).getFileName());
            var partial = directory.resolve(target.getFileName() + ".part");
            var digest = MessageDigest.getInstance("SHA-256");
            var response = get(update.installerUri(), "application/octet-stream");
            try (var body = response.body()) {
                copyUnlessStalled(body, partial, digest, response.headers().firstValueAsLong("Content-Length").orElse(0), progress);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected)) {
                Files.deleteIfExists(partial);
                throw new IllegalStateException("The downloaded update is corrupted (checksum mismatch)");
            }
            return Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not download the update: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The installer starts once this process has exited; the caller must quit right after. */
    public void installAfterExit(Path msi) {
        installer.launchAfterExit(msi);
    }

    private String expectedChecksum(AvailableUpdate update) throws IOException {
        try (var body = get(update.checksumUri(), "text/plain").body()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8).strip().split("\\s+")[0];
        }
    }

    private HttpResponse<InputStream> get(URI uri, String accept) throws IOException {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(properties.requestTimeoutSeconds())).header("Accept", accept).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /** A body that stops delivering bytes for the request timeout is closed, which fails the blocked read instead of hanging the download. */
    private void copyUnlessStalled(InputStream from, Path to, MessageDigest digest, long total, DoubleConsumer progress) throws IOException {
        var lastBytesAt = new AtomicLong(System.nanoTime());
        var stallNanos = Duration.ofSeconds(properties.requestTimeoutSeconds()).toNanos();
        var watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> Thread.ofPlatform().daemon().name("update-download-watchdog").unstarted(runnable));
        watchdog.scheduleWithFixedDelay(() -> {
            if (System.nanoTime() - lastBytesAt.get() > stallNanos) {
                closeQuietly(from);
            }
        }, 1, 1, TimeUnit.SECONDS);
        try {
            copy(from, to, digest, total, progress, lastBytesAt);
        } finally {
            watchdog.shutdownNow();
        }
    }

    /** Only the installers of earlier versions are in the folder, so none of them is needed again. */
    private static void deleteOldDownloads(Path directory) throws IOException {
        try (var files = Files.newDirectoryStream(directory)) {
            for (var file : files) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // the read that is blocked on it reports the failure
        }
    }

    private static void copy(InputStream from, Path to, MessageDigest digest, long total, DoubleConsumer progress, AtomicLong lastBytesAt) throws IOException {
        var buffer = new byte[COPY_BUFFER_BYTES];
        long done = 0;
        try (var out = Files.newOutputStream(to)) {
            int read;
            while ((read = from.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                lastBytesAt.set(System.nanoTime());
                digest.update(buffer, 0, read);
                done += read;
                if (total > 0) {
                    progress.accept((double) done / total);
                }
            }
        }
    }
}
