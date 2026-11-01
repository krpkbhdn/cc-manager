package com.github.krpkbhdn.ccmanager.voice.asset;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.download.VerifiedDownload;
import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import org.springframework.stereotype.Component;

/** Keeps voice runtimes and models under {@code <dataDir>/voice}; a path exists only once its install has completed. */
@Component
public class VoiceAssetService {

    private static final String VOICE_DIRECTORY = "voice";
    private static final String DOWNLOAD_PREFIX = "download-";
    private static final String PARTIAL_SUFFIX = ".part";

    private final Path root;
    private final VerifiedDownload downloads;
    private final VoiceProperties properties;

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

    public synchronized Path install(VoiceAsset asset, DoubleConsumer progress) throws IOException {
        var target = root.resolve(asset.relativePath());
        if (Files.exists(target)) {
            return target;
        }
        Files.createDirectories(root);
        deleteStaleDownloads();
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

    /** Renaming first proves nothing holds the files; a half-deleted runtime never counts as installed. */
    public synchronized void remove(VoiceAsset asset) throws IOException {
        var target = root.resolve(asset.relativePath());
        var removing = target.resolveSibling(target.getFileName() + ".removing");
        ZipExtraction.deleteRecursively(removing);
        if (!Files.exists(target)) {
            return;
        }
        try {
            Files.move(target, removing, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IOException(asset.name() + " is in use and cannot be removed; stop voice input and try again", e);
        }
        ZipExtraction.deleteRecursively(removing);
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
