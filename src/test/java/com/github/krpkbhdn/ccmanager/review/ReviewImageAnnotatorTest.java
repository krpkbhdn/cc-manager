package com.github.krpkbhdn.ccmanager.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewImageAnnotatorTest {

    private static final int SIZE = 200;

    private final ReviewImageAnnotator annotator = new ReviewImageAnnotator();

    @TempDir
    Path dir;

    private Path image(String name, String format, int type) throws IOException {
        BufferedImage img = new BufferedImage(SIZE, SIZE, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, SIZE, SIZE);
        g.dispose();
        Path path = dir.resolve(name);
        ImageIO.write(img, format, path.toFile());
        return path;
    }

    private static ReviewMark mark(MarkKind kind, double x, double y, double w, double h) {
        return new ReviewMark(1, 1, kind, x, y, w, h, "c");
    }

    @Test
    void drawsRectangleEdgeAndLeavesFarPixelsUnchanged() throws IOException {
        Path source = image("a.png", "png", BufferedImage.TYPE_INT_RGB);
        Path target = dir.resolve("out.png");

        annotator.annotate(source, target, List.of(mark(MarkKind.RECTANGLE, 0.25, 0.25, 0.5, 0.5)));

        BufferedImage out = ImageIO.read(target.toFile());
        assertThat(out.getWidth()).isEqualTo(SIZE);
        assertThat(out.getHeight()).isEqualTo(SIZE);
        assertThat(out.getRGB(100, 50) & 0xFFFFFF).isNotEqualTo(0xFFFFFF);
        assertThat(out.getRGB(190, 190) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void drawsFreeformPolygonEdgeAndLeavesFarPixelsUnchanged() throws IOException {
        Path source = image("a.png", "png", BufferedImage.TYPE_INT_RGB);
        Path target = dir.resolve("out.png");
        var points = List.of(new MarkPoint(0.25, 0.5), new MarkPoint(0.75, 0.5), new MarkPoint(0.5, 0.9));

        annotator.annotate(source, target, List.of(new ReviewMark(1, 1, MarkKind.FREEFORM, 0.25, 0.5, 0.5, 0.4, "c", points)));

        BufferedImage out = ImageIO.read(target.toFile());
        assertThat(out.getRGB(100, 100) & 0xFFFFFF).isNotEqualTo(0xFFFFFF);
        assertThat(out.getRGB(190, 20) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void worksForJpegSource() throws IOException {
        Path source = image("a.jpg", "jpg", BufferedImage.TYPE_INT_RGB);
        Path target = dir.resolve("out.png");

        annotator.annotate(source, target, List.of(mark(MarkKind.RECTANGLE, 0.25, 0.25, 0.5, 0.5)));

        assertThat(ImageIO.read(target.toFile()).getWidth()).isEqualTo(SIZE);
    }

    @Test
    void pinEllipseAndEdgeMarksDoNotThrowAndKeepSize() throws IOException {
        Path source = image("a.png", "png", BufferedImage.TYPE_INT_RGB);
        Path target = dir.resolve("out.png");
        List<ReviewMark> marks = List.of(
                mark(MarkKind.PIN, 0.5, 0.5, 0, 0),
                mark(MarkKind.ELLIPSE, 0.1, 0.1, 0.3, 0.3),
                mark(MarkKind.PIN, 1.0, 1.0, 0, 0),
                mark(MarkKind.RECTANGLE, 0, 0, 1, 1));

        assertThatCode(() -> annotator.annotate(source, target, marks)).doesNotThrowAnyException();

        BufferedImage out = ImageIO.read(target.toFile());
        assertThat(out.getWidth()).isEqualTo(SIZE);
        assertThat(out.getHeight()).isEqualTo(SIZE);
    }
}
