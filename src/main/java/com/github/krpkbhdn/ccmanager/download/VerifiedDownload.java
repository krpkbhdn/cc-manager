package com.github.krpkbhdn.ccmanager.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import org.springframework.stereotype.Component;

/** Downloads a file next to its target as {@code .part}, verifies its SHA-256 and only then moves it into place. */
@Component
public class VerifiedDownload {

    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    /**
     * @param stall how long the connection may stay silent before the download fails
     * @param progress receives 0..1 while downloading (not called when the size is unknown)
     * @throws IllegalStateException when the checksum does not match; the partial file is deleted
     */
    public Path fetch(URI url, String sha256, Path target, Duration stall, DoubleConsumer progress) throws IOException {
        return fetch(url, sha256, target, stall, progress, Long.MAX_VALUE);
    }

    /** As above, but a body longer than {@code maxBytes} aborts the download with an {@link IOException}. */
    public Path fetch(URI url, String sha256, Path target, Duration stall, DoubleConsumer progress, long maxBytes) throws IOException {
        var partial = target.resolveSibling(target.getFileName() + ".part");
        Throwable failure = null;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var response = get(url, "application/octet-stream", stall);
            try (var body = response.body()) {
                copyUnlessStalled(body, partial, digest, response.headers().firstValueAsLong("Content-Length").orElse(0), maxBytes, stall, progress);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha256)) {
                throw new IllegalStateException("The downloaded file is corrupted (checksum mismatch)");
            }
            return Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchAlgorithmException e) {
            failure = new IllegalStateException(e);
            throw (IllegalStateException) failure;
        } catch (IOException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            deleteKeepingFailure(partial, failure);
        }
    }

    private static void deleteKeepingFailure(Path file, Throwable failure) throws IOException {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e);
        }
    }

    /** A 200 response with an open body the caller must close; any other status is an {@link IOException}. */
    public HttpResponse<InputStream> get(URI uri, String accept, Duration timeout) throws IOException {
        var request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", accept).build();
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

    /** A body that stops delivering bytes for the stall time is closed, which fails the blocked read instead of hanging the download. */
    private static void copyUnlessStalled(InputStream from, Path to, MessageDigest digest, long total, long maxBytes, Duration stall, DoubleConsumer progress) throws IOException {
        var lastBytesAt = new AtomicLong(System.nanoTime());
        var stallNanos = stall.toNanos();
        var watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> Thread.ofPlatform().daemon().name("download-watchdog").unstarted(runnable));
        watchdog.scheduleWithFixedDelay(() -> {
            if (System.nanoTime() - lastBytesAt.get() > stallNanos) {
                closeQuietly(from);
            }
        }, 1, 1, TimeUnit.SECONDS);
        try {
            copy(from, to, digest, total, maxBytes, progress, lastBytesAt);
        } finally {
            watchdog.shutdownNow();
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // the read that is blocked on it reports the failure
        }
    }

    private static void copy(InputStream from, Path to, MessageDigest digest, long total, long maxBytes, DoubleConsumer progress, AtomicLong lastBytesAt) throws IOException {
        var buffer = new byte[COPY_BUFFER_BYTES];
        long done = 0;
        try (var out = Files.newOutputStream(to)) {
            int read;
            while ((read = from.read(buffer)) > 0) {
                done += read;
                if (done > maxBytes) {
                    throw new IOException("The download is larger than the expected " + maxBytes + " bytes");
                }
                out.write(buffer, 0, read);
                lastBytesAt.set(System.nanoTime());
                digest.update(buffer, 0, read);
                if (total > 0) {
                    progress.accept((double) done / total);
                }
            }
        }
    }
}
