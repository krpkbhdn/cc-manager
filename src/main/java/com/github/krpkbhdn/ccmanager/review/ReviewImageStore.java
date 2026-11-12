package com.github.krpkbhdn.ccmanager.review;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Copies screenshots an agent submitted into a review folder under neutral names. Each file is opened once, copied with a size cap and
 * only the copy is validated, so the source cannot change between the check and the copy.
 */
@Component
public class ReviewImageStore {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final String DIRECTORY_PERMISSIONS = "rwx------";
    private static final String FILE_PERMISSIONS = "rw-------";
    private static final String STAGING_SUFFIX = ".part";
    private static final int COPY_BUFFER_BYTES = 8192;
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final Pattern RESERVED_DEVICE_NAME = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");

    private record Checked(Path file, String label, int width, int height) {
    }

    private final ReviewProperties properties;

    public ReviewImageStore(ReviewProperties properties) {
        this.properties = properties;
    }

    public List<StoredImage> store(Path directory, List<SubmittedImage> images) {
        return store(directory, images, 1);
    }

    /** Names the files image-{firstNumber}.. so an append never overwrites a stored image. */
    public List<StoredImage> store(Path directory, List<SubmittedImage> images, int firstNumber) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("no images submitted");
        }
        if (images.size() > properties.maxImages()) {
            throw new IllegalArgumentException("too many images: " + images.size() + ", at most " + properties.maxImages());
        }
        for (int i = 0; i < images.size(); i++) {
            requireLocalPath(i + 1, images.get(i));
        }
        boolean directoryExisted = Files.exists(directory);
        List<Path> created = new ArrayList<>();
        try {
            createSecureDirectory(directory);
            List<StoredImage> stored = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                stored.add(copyAndValidate(firstNumber + i, images.get(i), directory, created));
            }
            return stored;
        } catch (IOException | RuntimeException e) {
            created.forEach(file -> file.toFile().delete());
            if (!directoryExisted) {
                directory.toFile().delete();
            }
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        }
    }

    private static void requireLocalPath(int number, SubmittedImage image) {
        String prefix = "image " + number + ": ";
        if (image == null || image.path() == null) {
            throw new IllegalArgumentException(prefix + "path must be absolute");
        }
        requireLocalPath(image.path().toString(), WINDOWS, prefix);
        if (!image.path().isAbsolute()) {
            throw new IllegalArgumentException(prefix + "path must be absolute");
        }
    }

    /** Rejects network and extended-length paths, and on Windows device names, before the path is used for anything. */
    static void requireLocalPath(String path, boolean windows, String prefix) {
        if (path.startsWith("\\\\") || path.startsWith("//")) {
            throw new IllegalArgumentException(prefix + "network and extended-length paths are not allowed");
        }
        if (windows && Arrays.stream(path.split("[\\\\/]")).map(ReviewImageStore::withoutExtension).anyMatch(name -> RESERVED_DEVICE_NAME.matcher(name).matches())) {
            throw new IllegalArgumentException(prefix + "device names are not allowed in the path");
        }
    }

    private static String withoutExtension(String element) {
        int dot = element.indexOf('.');
        return (dot < 0 ? element : element.substring(0, dot)).stripTrailing();
    }

    private StoredImage copyAndValidate(int number, SubmittedImage image, Path directory, List<Path> created) throws IOException {
        String prefix = "image " + number + ": ";
        Path staging = directory.resolve("image-" + number + STAGING_SUFFIX);
        created.add(staging);
        try {
            copyCapped(image.path(), staging, prefix);
            restrictToOwner(staging, FILE_PERMISSIONS);
            String extension = detectExtension(staging, prefix);
            int[] size = readSize(staging, prefix);
            reencode(staging, extension, prefix);
            String fileName = "image-" + number + "." + extension;
            Files.move(staging, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            created.add(directory.resolve(fileName));
            return new StoredImage(fileName, labelOf(image), size[0], size[1]);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException(prefix + "file does not exist");
        } catch (IOException e) {
            throw new IllegalArgumentException(prefix + "file cannot be read");
        }
    }

    private void copyCapped(Path source, Path target, String prefix) throws IOException {
        Path real = source.toRealPath();
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(prefix + "not a regular file");
        }
        long limit = properties.maxImageBytes();
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long total = 0;
        try (InputStream in = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS);
                OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > limit) {
                    throw new IllegalArgumentException(prefix + "file is larger than " + limit + " bytes");
                }
                out.write(buffer, 0, read);
            }
        }
    }

    private String detectExtension(Path file, String prefix) throws IOException {
        byte[] head = new byte[PNG_MAGIC.length];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(head, 0, head.length);
            head = Arrays.copyOf(head, read);
        }
        if (startsWith(head, PNG_MAGIC)) {
            return "png";
        }
        if (startsWith(head, JPEG_MAGIC)) {
            return "jpg";
        }
        throw new IllegalArgumentException(prefix + "not a PNG or JPEG image");
    }

    /** Drops anything appended to the image (polyglot files): only the decoded pixels are kept. */
    private void reencode(Path file, String extension, String prefix) throws IOException {
        BufferedImage pixels = ImageIO.read(file.toFile());
        if (pixels == null) {
            throw new IllegalArgumentException(prefix + "image cannot be decoded");
        }
        Path clean = file.resolveSibling(file.getFileName() + ".clean");
        try {
            if (!ImageIO.write(pixels, "jpg".equals(extension) ? "jpeg" : extension, clean.toFile())) {
                throw new IllegalArgumentException(prefix + "image cannot be decoded");
            }
            restrictToOwner(clean, FILE_PERMISSIONS);
            Files.move(clean, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(clean);
        }
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        return data.length >= magic.length && Arrays.equals(data, 0, magic.length, magic, 0, magic.length);
    }

    private int[] readSize(Path file, String prefix) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            var readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException(prefix + "image cannot be decoded");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int side = properties.maxImageSide();
                if (width <= 0 || height <= 0 || width > side || height > side || (long) width * height > properties.maxImagePixels()) {
                    throw new IllegalArgumentException(prefix + "image dimensions are out of range");
                }
                return new int[] {width, height};
            } catch (IOException | RuntimeException e) {
                if (e instanceof IllegalArgumentException iae && iae.getMessage() != null && iae.getMessage().startsWith(prefix)) {
                    throw iae;
                }
                throw new IllegalArgumentException(prefix + "image cannot be decoded");
            } finally {
                reader.dispose();
            }
        }
    }

    private static String labelOf(SubmittedImage image) {
        if (image.label() != null && !image.label().isBlank()) {
            return image.label();
        }
        String name = image.path().getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void createSecureDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        restrictToOwner(directory, DIRECTORY_PERMISSIONS);
    }

    private static void restrictToOwner(Path path, String permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }
    }
}
