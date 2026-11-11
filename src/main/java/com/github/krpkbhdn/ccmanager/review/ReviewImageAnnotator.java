package com.github.krpkbhdn.ccmanager.review;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Draws the numbered review marks onto a copy of a screenshot so the agent sees what the user meant. */
@Component
public class ReviewImageAnnotator {

    private static final Color MARK_COLOR = new Color(230, 30, 40);
    private static final int MIN_STROKE_PX = 2;
    private static final double STROKE_RATIO = 0.003;
    private static final int MIN_BADGE_PX = 16;
    private static final double BADGE_RATIO = 0.025;
    private static final double PIN_RADIUS_FACTOR = 2.5;

    public void annotate(Path source, Path target, List<ReviewMark> marks) {
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) {
                throw new IOException("Unreadable image " + source);
            }
            BufferedImage canvas = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.drawImage(original, 0, 0, null);
                int stroke = Math.max(MIN_STROKE_PX, (int) Math.round(Math.max(canvas.getWidth(), canvas.getHeight()) * STROKE_RATIO));
                int badge = Math.max(MIN_BADGE_PX, (int) Math.round(Math.min(canvas.getWidth(), canvas.getHeight()) * BADGE_RATIO));
                for (int i = 0; i < marks.size(); i++) {
                    drawMark(g, marks.get(i), canvas.getWidth(), canvas.getHeight(), stroke);
                    drawBadge(g, marks.get(i), i + 1, canvas.getWidth(), canvas.getHeight(), badge);
                }
            } finally {
                g.dispose();
            }
            Files.createDirectories(target.toAbsolutePath().getParent());
            ImageIO.write(canvas, "png", target.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void drawMark(Graphics2D g, ReviewMark mark, int width, int height, int stroke) {
        int x = (int) Math.round(mark.x() * width);
        int y = (int) Math.round(mark.y() * height);
        int w = (int) Math.round(mark.w() * width);
        int h = (int) Math.round(mark.h() * height);
        g.setColor(MARK_COLOR);
        g.setStroke(new BasicStroke(stroke));
        switch (mark.kind()) {
            case RECTANGLE -> g.drawRect(x, y, w, h);
            case ELLIPSE -> g.drawOval(x, y, w, h);
            case PIN -> drawPin(g, x, y, stroke);
            case FREEFORM -> drawPolygon(g, mark.points(), width, height, stroke);
        }
    }

    private void drawPolygon(Graphics2D g, List<MarkPoint> points, int width, int height, int stroke) {
        int[] xs = points.stream().mapToInt(p -> (int) Math.round(p.x() * width)).toArray();
        int[] ys = points.stream().mapToInt(p -> (int) Math.round(p.y() * height)).toArray();
        g.drawPolygon(xs, ys, xs.length);
        int dot = stroke * 2;
        for (int i = 0; i < xs.length; i++) {
            g.fillOval(xs[i] - dot / 2, ys[i] - dot / 2, dot, dot);
        }
    }

    private void drawPin(Graphics2D g, int x, int y, int stroke) {
        int dot = stroke * 2;
        g.fillOval(x - dot / 2, y - dot / 2, dot, dot);
        int ring = (int) Math.round(stroke * PIN_RADIUS_FACTOR * 2);
        g.drawOval(x - ring, y - ring, ring * 2, ring * 2);
    }

    private void drawBadge(Graphics2D g, ReviewMark mark, int number, int width, int height, int size) {
        int x = clamp((int) Math.round(mark.x() * width), 0, width - size);
        int y = clamp((int) Math.round(mark.y() * height), 0, height - size);
        g.setColor(MARK_COLOR);
        g.fillOval(x, y, size, size);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(1, size * 3 / 5)));
        FontMetrics metrics = g.getFontMetrics();
        String text = Integer.toString(number);
        g.drawString(text, x + (size - metrics.stringWidth(text)) / 2, y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }
}
