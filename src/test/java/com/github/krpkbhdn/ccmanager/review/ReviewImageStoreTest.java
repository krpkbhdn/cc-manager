package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewImageStoreTest {

    @TempDir
    Path temp;

    private final ReviewImageStore store = storeWith(20, 1_000_000);

    private static ReviewImageStore storeWith(int maxImages, long maxBytes) {
        return new ReviewImageStore(new ReviewProperties(Duration.ofDays(1), Duration.ofHours(1), maxImages, maxBytes, 8192, 36_000_000L, 50));
    }

    @Test
    void rejectsNetworkAndExtendedPathsBeforeTouchingTheFileSystem() {
        for (String path : List.of("\\\\host\\share\\a.png", "//host/share/a.png", "\\\\?\\C:\\a.png")) {
            assertThatThrownBy(() -> ReviewImageStore.requireLocalPath(path, false, "image 1: ")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("image 1: network and extended-length paths are not allowed");
        }
        assertThatThrownBy(() -> store.store(temp.resolve("out"), List.of(new SubmittedImage(Path.of("\\\\host\\share\\a.png"), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image 1");
        assertThat(temp.resolve("out")).doesNotExist();
    }

    @Test
    void firstNumberKeepsTheImagesAlreadyStored() throws IOException {
        Path png = image("a.png", "png", 8, 8);
        var out = temp.resolve("append");

        store.store(out, List.of(new SubmittedImage(png, null)));
        var appended = store.store(out, List.of(new SubmittedImage(png, null)), 2);

        assertThat(appended).extracting(StoredImage::fileName).containsExactly("image-2.png");
        assertThat(out.resolve("image-1.png")).exists();
        assertThat(out.resolve("image-2.png")).exists();
    }

    @Test
    void rejectsWindowsDeviceNamesOnlyOnWindows() {
        for (String path : List.of("C:\\dir\\NUL", "C:\\dir\\con.png", "C:/x/lpt1.txt/a.png", "C:\\Aux")) {
            assertThatThrownBy(() -> ReviewImageStore.requireLocalPath(path, true, "image 1: ")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("image 1: device names are not allowed in the path");
            assertThatCode(() -> ReviewImageStore.requireLocalPath(path, false, "image 1: ")).doesNotThrowAnyException();
        }
        assertThatCode(() -> ReviewImageStore.requireLocalPath("C:\\dir\\console.png", true, "image 1: ")).doesNotThrowAnyException();
    }

    @Test
    void rejectsImagesBeyondTheConfiguredSideAndPixelLimits() throws IOException {
        Path png = image("wide.png", "png", 30, 10);
        var tight = new ReviewImageStore(new ReviewProperties(Duration.ofDays(1), Duration.ofHours(1), 5, 1_000_000, 20, 36_000_000L, 50));
        var fewPixels = new ReviewImageStore(new ReviewProperties(Duration.ofDays(1), Duration.ofHours(1), 5, 1_000_000, 8192, 100, 50));

        assertThatThrownBy(() -> tight.store(temp.resolve("o1"), List.of(new SubmittedImage(png, null)))).hasMessage("image 1: image dimensions are out of range");
        assertThatThrownBy(() -> fewPixels.store(temp.resolve("o2"), List.of(new SubmittedImage(png, null)))).hasMessage("image 1: image dimensions are out of range");
        assertThat(temp.resolve("o1")).doesNotExist();
    }

    @Test
    void storesA2560WideImageUntouchedWithTheDefaultLimits() throws IOException {
        Path png = image("wide2560.png", "png", 2560, 1080);
        var defaults = new ReviewImageStore(new ReviewProperties(Duration.ofDays(1), Duration.ofHours(1), 5, 52_428_800L, 32_767, 64_000_000L, 50));

        var stored = defaults.store(temp.resolve("hd"), List.of(new SubmittedImage(png, null)));

        assertThat(stored.get(0).width()).isEqualTo(2560);
        assertThat(stored.get(0).height()).isEqualTo(1080);
    }

    @Test
    void symlinkCopiesTheBytesOfItsTargetAndLeavesNoStagingFile() throws IOException {
        Path png = image("real.png", "png", 8, 8);
        Path link;
        try {
            link = Files.createSymbolicLink(temp.resolve("link.png"), png);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symlinks not available");
            return;
        }
        Path out = temp.resolve("out");

        store.store(out, List.of(new SubmittedImage(link, null)));

        assertThat(Files.readAllBytes(out.resolve("image-1.png"))).isEqualTo(Files.readAllBytes(png));
        assertThat(Files.list(out).map(p -> p.getFileName().toString())).containsExactly("image-1.png");
    }

    @Test
    void symlinkToTextIsRejectedAfterCopyingAndNothingRemains() throws IOException {
        Path text = Files.writeString(temp.resolve("note.txt"), "plain text, not an image at all");
        Path link;
        try {
            link = Files.createSymbolicLink(temp.resolve("bad.png"), text);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symlinks not available");
            return;
        }
        Path out = temp.resolve("out");

        assertThatThrownBy(() -> store.store(out, List.of(new SubmittedImage(link, null)))).hasMessage("image 1: not a PNG or JPEG image");

        assertThat(out).doesNotExist();
    }

    @Test
    void failureKeepsAPreexistingFolderButRemovesWhatWasCopied() throws IOException {
        Path png = image("ok.png", "png", 5, 5);
        Path out = Files.createDirectory(temp.resolve("out"));

        assertThatThrownBy(() -> store.store(out, List.of(new SubmittedImage(png, null), new SubmittedImage(temp.resolve("missing.png"), null))))
                .hasMessage("image 2: file does not exist");

        assertThat(out).isEmptyDirectory();
    }

    private Path image(String name, String format, int width, int height) throws IOException {
        Path file = temp.resolve(name);
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, file.toFile());
        return file;
    }

    @Test
    void acceptsPngAndJpgWithNamesLabelsAndDimensions() throws IOException {
        Path png = image("login.png", "png", 30, 20);
        Path jpg = image("home.jpeg", "jpg", 10, 40);
        Path out = temp.resolve("out/nested");

        List<StoredImage> stored = store.store(out, List.of(new SubmittedImage(png, null), new SubmittedImage(jpg, "Home page")));

        assertThat(stored).containsExactly(new StoredImage("image-1.png", "login", 30, 20), new StoredImage("image-2.jpg", "Home page", 10, 40));
        assertThat(out.resolve("image-1.png")).exists();
        assertThat(out.resolve("image-2.jpg")).exists();
    }

    @Test
    void dropsDataAppendedAfterTheImage() throws IOException {
        Path png = image("poly.png", "png", 8, 8);
        Files.write(png, "SECRET-TRAILER".getBytes(), java.nio.file.StandardOpenOption.APPEND);
        Path out = temp.resolve("out");

        store.store(out, List.of(new SubmittedImage(png, null)));

        assertThat(new String(Files.readAllBytes(out.resolve("image-1.png")), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("SECRET-TRAILER");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> store.store(temp.resolve("out"), List.of(new SubmittedImage(temp.resolve("nope.png"), ""))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("image 1: file does not exist");
    }

    @Test
    void rejectsDirectory() throws IOException {
        Path dir = Files.createDirectory(temp.resolve("dir.png"));
        assertThatThrownBy(() -> store.store(temp.resolve("out"), List.of(new SubmittedImage(dir, null)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image 1");
    }

    @Test
    void rejectsRelativePath() {
        assertThatThrownBy(() -> store.store(temp.resolve("out"), List.of(new SubmittedImage(Path.of("a.png"), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("image 1: path must be absolute");
    }

    @Test
    void rejectsTextFileRenamedPng() throws IOException {
        Path fake = Files.writeString(temp.resolve("fake.png"), "hello world, not an image");
        assertThatThrownBy(() -> store.store(temp.resolve("out"), List.of(new SubmittedImage(fake, null)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("image 1: not a PNG or JPEG image");
    }

    @Test
    void rejectsOversizeFile() throws IOException {
        Path png = image("big.png", "png", 50, 50);
        assertThatThrownBy(() -> storeWith(5, 10).store(temp.resolve("out"), List.of(new SubmittedImage(png, null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("larger than");
    }

    @Test
    void rejectsTooManyAndEmpty() throws IOException {
        Path png = image("a.png", "png", 5, 5);
        assertThatThrownBy(() -> storeWith(1, 1_000_000).store(temp.resolve("out"), List.of(new SubmittedImage(png, null), new SubmittedImage(png, null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too many");
        assertThatThrownBy(() -> store.store(temp.resolve("out"), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void followsSymlinkToImageButRejectsSymlinkToText() throws IOException {
        Path png = image("real.png", "png", 8, 8);
        Path text = Files.writeString(temp.resolve("note.txt"), "plain text");
        Path goodLink;
        Path badLink;
        try {
            goodLink = Files.createSymbolicLink(temp.resolve("good-link.png"), png);
            badLink = Files.createSymbolicLink(temp.resolve("bad-link.png"), text);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symlinks not available");
            return;
        }
        assertThat(store.store(temp.resolve("out"), List.of(new SubmittedImage(goodLink, null)))).hasSize(1);
        assertThatThrownBy(() -> store.store(temp.resolve("out2"), List.of(new SubmittedImage(badLink, null)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesNothingWhenSecondImageIsInvalid() throws IOException {
        Path png = image("ok.png", "png", 5, 5);
        Path out = temp.resolve("out");
        assertThatThrownBy(() -> store.store(out, List.of(new SubmittedImage(png, null), new SubmittedImage(temp.resolve("missing.png"), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("image 2: file does not exist");
        assertThat(out).doesNotExist();
    }
}
