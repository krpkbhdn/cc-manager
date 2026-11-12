package com.github.krpkbhdn.ccmanager.web;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.ByteArrayInputStream;
import java.nio.file.LinkOption;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/** PNG element screenshots under {@code <dataDir>/web-review/<projectId>/<revisionId>/item-<n>.png}; paths are built from numbers only. */
@Component
public class WebScreenshotStore {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static final int MAX_SIDE = 8192;
    private static final long MAX_PIXELS = 36_000_000L;

    private final Path root;
    private final WebReviewProperties properties;

    public WebScreenshotStore(AppProperties app, WebReviewProperties properties) {
        this.root = app.dataDir().resolve("web-review");
        this.properties = properties;
    }

    public Path directory(long projectId, long revisionId) {
        return root.resolve(Long.toString(projectId)).resolve(Long.toString(revisionId));
    }

    public static String fileName(int n) {
        return "item-" + n + ".png";
    }

    /** @return the file name inside the revision directory
     * @throws IllegalArgumentException when the bytes are not a PNG or exceed the size cap */
    public String save(long projectId, long revisionId, int n, byte[] png) {
        if (png == null || png.length < PNG_MAGIC.length || !Arrays.equals(png, 0, PNG_MAGIC.length, PNG_MAGIC, 0, PNG_MAGIC.length)) {
            throw new IllegalArgumentException("The screenshot is not a PNG image.");
        }
        if (png.length > properties.maxScreenshotBytes()) {
            throw new IllegalArgumentException("The screenshot is larger than " + properties.maxScreenshotBytes() + " bytes.");
        }
        requireDecodable(png);
        try {
            var directory = directory(projectId, revisionId);
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName(n)), png);
            return fileName(n);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads the dimensions first so a small file cannot force a huge decode, then decodes it fully to reject truncated data. */
    private static void requireDecodable(byte[] png) {
        var readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new IllegalStateException("No PNG reader is available.");
        }
        var reader = readers.next();
        int width = 0;
        int height = 0;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            reader.setInput(input);
            width = reader.getWidth(0);
            height = reader.getHeight(0);
            if (width <= MAX_SIDE && height <= MAX_SIDE && (long) width * height <= MAX_PIXELS) {
                reader.read(0);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The screenshot is not a valid PNG image.", e);
        } finally {
            reader.dispose();
        }
        if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("The screenshot is larger than " + MAX_SIDE + " pixels on a side.");
        }
    }

    public void move(long projectId, long fromRevisionId, long toRevisionId, int n) {
        var source = directory(projectId, fromRevisionId).resolve(fileName(n));
        try {
            if (Files.exists(source)) {
                Files.createDirectories(directory(projectId, toRevisionId));
                Files.move(source, directory(projectId, toRevisionId).resolve(fileName(n)), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes the revision's screenshot folder and its web-review-r&lt;number&gt;.md. */
    public void deleteRevision(long projectId, long revisionId, int number) {
        var directory = directory(projectId, revisionId);
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                TreeDeleter.delete(directory);
            }
            Files.deleteIfExists(directory.resolveSibling("web-review-r" + number + ".md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(long projectId, long revisionId, int n) {
        try {
            Files.deleteIfExists(directory(projectId, revisionId).resolve(fileName(n)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
