package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewFeedbackWriterTest {

    private final ReviewFeedbackWriter writer = new ReviewFeedbackWriter(new ReviewImageAnnotator());

    @TempDir
    Path dir;

    private Review review(String title) {
        return new Review(5, 1, "s", title, "a note", ReviewStatus.values()[0], Instant.now(), null);
    }

    private ReviewImage image(long id, int position) throws IOException {
        String name = "shot-" + id + ".png";
        ImageIO.write(new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB), "png", dir.resolve(name).toFile());
        return new ReviewImage(id, 5, position, "Screen " + id, name, 100, 50);
    }

    private static ReviewMark mark(long imageId, MarkKind kind, String comment) {
        return new ReviewMark(1, imageId, kind, 0.1, 0.2, kind == MarkKind.PIN ? 0 : 0.3, kind == MarkKind.PIN ? 0 : 0.4, comment);
    }

    @Test
    void freeformIsDescribedByBoundingBoxAndVertexList() throws IOException {
        var points = List.of(new MarkPoint(0.1, 0.2), new MarkPoint(0.4, 0.2), new MarkPoint(0.4, 0.6));
        var freeform = new ReviewMark(1, 1, MarkKind.FREEFORM, 0.1, 0.2, 0.3, 0.4, "round", points);

        ReviewFeedback feedback = writer.write(review("T"), List.of(image(1, 0)), Map.of(1L, List.of(freeform)), dir);

        assertThat(Files.readString(feedback.markdown(), StandardCharsets.UTF_8))
                .contains("#1 freeform with 3 points, bounding box x=10.0% y=20.0% w=30.0% h=40.0% (px: 10,10,30,20")
                .contains("points: (10.0,20.0) (40.0,20.0) (40.0,60.0)");
    }

    @Test
    void numbersRestartOnEveryImageAndOnlyMarkedImagesAreAnnotated() throws IOException {
        ReviewImage first = image(1, 0);
        ReviewImage second = image(2, 1);
        ReviewImage third = image(3, 2);
        Map<Long, List<ReviewMark>> marks = Map.of(
                1L, List.of(mark(1, MarkKind.RECTANGLE, "too wide")),
                3L, List.of(mark(3, MarkKind.PIN, ""), mark(3, MarkKind.ELLIPSE, "round it")));

        ReviewFeedback feedback = writer.write(review("Login"), List.of(first, second, third), marks, dir);

        String md = Files.readString(feedback.markdown(), StandardCharsets.UTF_8);
        assertThat(md).contains("## Image 1: Screen 1", "## Image 3: Screen 3", "image <k> #<n>")
                .contains("#1 rectangle at x=10.0%, y=20.0% size w=30.0% h=40.0% (px: 10,10,30,20", "too wide")
                .contains("#1 pin at x=10.0%, y=20.0% (px: 10,10 of the original image)", "(no comment)")
                .contains("#2 ellipse", "round it")
                .doesNotContain("#3")
                .contains("shot-1.png", "annotated-1.png", "annotated-3.png", "submit_screenshots");
        assertThat(md).doesNotContain("annotated-2.png");
        assertThat(dir.resolve("annotated-1.png")).exists();
        assertThat(dir.resolve("annotated-2.png")).doesNotExist();
        assertThat(dir.resolve("annotated-3.png")).exists();
    }

    @Test
    void promptIsOneLineWithAbsolutePathAndId() throws IOException {
        ReviewFeedback feedback = writer.write(review("Multi\nline\r\ntitle " + "x".repeat(200)),
                List.of(image(1, 0)), Map.of(1L, List.of(mark(1, MarkKind.PIN, "c"))), dir);

        assertThat(feedback.prompt()).doesNotContain("\n").doesNotContain("\r")
                .contains("#5").contains("read \"" + feedback.markdown() + "\" and").startsWith("Visual review #5 \"Multi line title");
        assertThat(feedback.markdown()).isAbsolute();
        assertThat(feedback.prompt().length()).isLessThan(feedback.markdown().toString().length() + 250);
    }

    @Test
    void promptStripsControlSeparatorAndBidiCharactersAndCapsTitle() throws IOException {
        String nasty = "a\u0000b\u0085c d e‮f⁦g⁩h\u001b[31mi\t\tj";
        ReviewFeedback feedback = writer.write(review(nasty + "y".repeat(200)), List.of(image(1, 0)), Map.of(), dir);

        assertThat(feedback.prompt()).startsWith("Visual review #5 \"a b c d e f g h [31mi j");
        assertThat(feedback.prompt().chars().filter(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029 || (c >= 0x202A && c <= 0x202E)
                || (c >= 0x2066 && c <= 0x2069)).count()).isZero();
        String title = feedback.prompt().split("\"")[1];
        assertThat(title).hasSizeLessThanOrEqualTo(80);
    }

    @Test
    void promptKeepsOneLineEvenWhenThePathHasControlCharacters() throws IOException {
        Path file = dir.resolve("sub dir‮x").resolve("review.md");

        String prompt = ReviewFeedbackWriter.prompt(review("t"), file);

        assertThat(prompt).doesNotContain(" ").doesNotContain("‮").contains("read \"" + dir).contains("sub dir x");
    }

    @Test
    void saysNothingWasMarkedWhenThereAreNoMarks() throws IOException {
        ReviewFeedback feedback = writer.write(review("Empty"), List.of(image(1, 0)), Map.of(), dir);

        assertThat(Files.readString(feedback.markdown(), StandardCharsets.UTF_8)).contains("marked nothing");
        assertThat(dir.resolve("annotated-1.png")).doesNotExist();
    }
}
