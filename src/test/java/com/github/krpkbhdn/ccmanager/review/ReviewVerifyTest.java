package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewVerifyTest {

    @TempDir
    Path temp;

    private Path png(String name, int w, int h) throws Exception {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) img.setRGB(x, y, 0xFFFFFF);
        var p = temp.resolve(name);
        ImageIO.write(img, "png", p.toFile());
        return p;
    }

    private ReviewProperties props() {
        return new ReviewProperties(Duration.ofDays(7), Duration.ofHours(1), 20, 20971520L, 8192, 36_000_000L, 50);
    }

    @Test
    void absolutePathWithDotDotSegmentsIsResolvedAndCopiedUnderNeutralName() throws Exception {
        var shot = png("a.png", 20, 20);
        Files.createDirectories(temp.resolve("sub"));
        var tricky = temp.resolve("sub").resolve("..").resolve("a.png");
        var stored = new ReviewImageStore(props()).store(temp.resolve("out"), List.of(new SubmittedImage(tricky, "")));
        assertThat(stored.get(0).fileName()).isEqualTo("image-1.png");
        assertThat(temp.resolve("out/image-1.png")).exists();
    }

    @Test
    void relativeTraversalIsRejectedAndNothingCopied() {
        var store = new ReviewImageStore(props());
        assertThatThrownBy(() -> store.store(temp.resolve("out2"), List.of(new SubmittedImage(Path.of("..", "a.png"), ""))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute");
        assertThat(temp.resolve("out2")).doesNotExist();
    }

    @Test
    void pinBadgeAndRectangleOutlineChangePixelsAtExpectedPlace() throws Exception {
        var src = png("b.png", 400, 400);
        var out = temp.resolve("o.png");
        new ReviewImageAnnotator().annotate(src, out, List.of(
                new ReviewMark(1, 1, MarkKind.RECTANGLE, 0.25, 0.25, 0.5, 0.5, "c"),
                new ReviewMark(2, 1, MarkKind.PIN, 0.8, 0.1, 0, 0, "p")));
        var img = ImageIO.read(out.toFile());
        assertThat(img.getRGB(200, 100) & 0xFFFFFF).isNotEqualTo(0xFFFFFF); // top edge of rectangle
        assertThat(img.getRGB(200, 200) & 0xFFFFFF).isEqualTo(0xFFFFFF);   // interior untouched
        assertThat(img.getRGB(320, 40) & 0xFFFFFF).isNotEqualTo(0xFFFFFF); // pin at (0.8,0.1)
        assertThat(img.getRGB(20, 380) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void promptStaysOneLineForTitleWithNewlinesAndQuotes() throws Exception {
        var review = new Review(9, 1, "s", "Line1\r\nLine2 \"quoted\"\n\nend " + "x".repeat(200), "note\nmultiline", ReviewStatus.PENDING, Instant.now(), null);
        var fb = new ReviewFeedbackWriter(new ReviewImageAnnotator()).write(review, List.of(), Map.of(), temp.resolve("fb"));
        assertThat(fb.prompt()).doesNotContain("\n").doesNotContain("\r").contains("#9");
        var md = Files.readString(fb.markdown(), StandardCharsets.UTF_8);
        assertThat(md).contains("Visual review: Line1").contains("The user marked nothing");
    }
}
