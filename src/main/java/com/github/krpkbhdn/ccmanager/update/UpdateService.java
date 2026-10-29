package com.github.krpkbhdn.ccmanager.update;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.download.VerifiedDownload;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
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

    private final UpdateProperties properties;
    private final AppProperties app;
    private final JsonMapper mapper;
    private final UpdateInstaller installer;
    private final VerifiedDownload downloads;

    UpdateService(UpdateProperties properties, AppProperties app, JsonMapper mapper, UpdateInstaller installer, VerifiedDownload downloads) {
        this.properties = properties;
        this.app = app;
        this.mapper = mapper;
        this.installer = installer;
        this.downloads = downloads;
    }

    public boolean enabled() {
        return properties.enabled() && ReleaseVersion.parse(properties.version()).isPresent();
    }

    public String currentVersion() {
        return properties.version();
    }

    public URI releasesUri() {
        return URI.create("https://github.com/" + properties.repository() + "/releases");
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
        try {
            return checkForUpdate();
        } catch (IllegalStateException e) {
            log.warn("Update check failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Like [findUpdate] but a failed request (offline, HTTP error, unreadable answer) is thrown as [IllegalStateException]. */
    public Optional<AvailableUpdate> checkForUpdate() {
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
            throw new IllegalStateException(e.toString(), e);
        }
    }

    /** @return the verified MSI; [progress] receives 0..1 while downloading (not called when the size is unknown) */
    public Path download(AvailableUpdate update, DoubleConsumer progress) {
        try {
            var expected = expectedChecksum(update);
            var directory = Files.createDirectories(app.dataDir().resolve(UPDATES_DIRECTORY));
            deleteOldDownloads(directory);
            var target = directory.resolve(Path.of(update.installerName()).getFileName());
            return downloads.fetch(update.installerUri(), expected, target, Duration.ofSeconds(properties.requestTimeoutSeconds()), progress);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not download the update: " + e.getMessage(), e);
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
        return downloads.get(uri, accept, Duration.ofSeconds(properties.requestTimeoutSeconds()));
    }

    /** Only the installers of earlier versions are in the folder, so none of them is needed again. */
    private static void deleteOldDownloads(Path directory) throws IOException {
        try (var files = Files.newDirectoryStream(directory)) {
            for (var file : files) {
                Files.deleteIfExists(file);
            }
        }
    }
}
