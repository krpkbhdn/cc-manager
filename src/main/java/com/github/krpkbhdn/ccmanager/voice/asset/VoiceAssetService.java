package com.github.krpkbhdn.ccmanager.voice.asset;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.download.VerifiedDownload;
import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleConsumer;
import org.springframework.stereotype.Component;

/** Keeps voice runtimes and models under {@code <dataDir>/voice}; a path exists only once its install has completed. */
@Component
public class VoiceAssetService {

    private static final String VOICE_DIRECTORY = "voice";
    private static final String DOWNLOAD_PREFIX = "download-";
    private static final String PARTIAL_SUFFIX = ".part";
    private static final int HASH_BUFFER_BYTES = 1 << 20;

    private final Path root;
    private final VerifiedDownload downloads;
    private final VoiceProperties properties;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private int activeInstalls;

    VoiceAssetService(AppProperties app, VerifiedDownload downloads, VoiceProperties properties) {
        this.root = app.dataDir().resolve(VOICE_DIRECTORY);
        this.downloads = downloads;
        this.properties = properties;
    }

    public boolean installed(VoiceAsset asset) {
        return Files.exists(root.resolve(asset.relativePath()));
    }

    public Optional<Path> pathOf(VoiceAsset asset) {
        return Optional.of(root.resolve(asset.relativePath())).filter(Files::exists);
    }

    /** Installs of different assets run in parallel; the same asset is downloaded once. */
    public Path install(VoiceAsset asset, DoubleConsumer progress) throws IOException {
        var target = root.resolve(asset.relativePath());
        synchronized (locks.computeIfAbsent(asset.relativePath(), key -> new Object())) {
            if (Files.exists(target)) {
                if (intact(asset, target)) {
                    return target;
                }
                ZipExtraction.deleteRecursively(target);
            }
            beginInstall();
            try {
                return download(asset, target, progress);
            } finally {
                endInstall();
            }
        }
    }

    /** Only a plain file can be re-hashed; an unpacked archive no longer matches the hash of its zip and was verified before extraction. */
    private static boolean intact(VoiceAsset asset, Path target) throws IOException {
        if (asset.archive()) {
            return true;
        }
        if (!Files.isRegularFile(target) || Files.size(target) != asset.sizeBytes()) {
            return false;
        }
        try (var in = Files.newInputStream(target)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[HASH_BUFFER_BYTES];
            for (int read; (read = in.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(asset.sha256());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Stale downloads are swept only while no other install is writing its own partial files. */
    private synchronized void beginInstall() throws IOException {
        Files.createDirectories(root);
        if (activeInstalls == 0) {
            deleteStaleDownloads();
        }
        activeInstalls++;
    }

    private synchronized void endInstall() {
        activeInstalls--;
    }

    private Path download(VoiceAsset asset, Path target, DoubleConsumer progress) throws IOException {
        if (!asset.archive()) {
            Files.createDirectories(target.getParent());
            return fetch(asset, target, progress);
        }
        var zip = root.resolve(DOWNLOAD_PREFIX + System.nanoTime() + ".zip");
        try {
            fetch(asset, zip, progress);
            Files.createDirectories(target.getParent());
            ZipExtraction.extract(zip, target);
        } finally {
            Files.deleteIfExists(zip);
        }
        return target;
    }

    private Path fetch(VoiceAsset asset, Path target, DoubleConsumer progress) throws IOException {
        var file = downloads.fetch(URI.create(asset.url()), asset.sha256(), target, properties.downloadStallTimeout(), progress, asset.sizeBytes());
        var size = Files.size(file);
        if (size != asset.sizeBytes()) {
            Files.deleteIfExists(file);
            throw new IOException(asset.name() + " has an unexpected size: " + size);
        }
        return file;
    }

    private void deleteStaleDownloads() throws IOException {
        try (var files = Files.walk(root)) {
            for (var file : files.filter(this::isStaleDownload).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** Archives are fetched as {@code download-*} in the root; plain models leave {@code <name>.part} next to their target. */
    private boolean isStaleDownload(Path path) {
        var name = path.getFileName().toString();
        return Files.isRegularFile(path) && (name.endsWith(PARTIAL_SUFFIX) || path.getParent().equals(root) && name.startsWith(DOWNLOAD_PREFIX));
    }
}
