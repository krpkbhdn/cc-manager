package com.github.krpkbhdn.ccmanager.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes the annotated images and the review.md the agent reads, plus the one-line prompt that points at it. */
@Component
public class ReviewFeedbackWriter {

    private static final int MAX_TITLE_CHARS = 80;
    private static final int MAX_LISTED_POINTS = 20;
    private static final double PERCENT = 100.0;
    private static final String MARKDOWN_FILE = "review.md";

    private final ReviewImageAnnotator annotator;

    public ReviewFeedbackWriter(ReviewImageAnnotator annotator) {
        this.annotator = annotator;
    }

    public ReviewFeedback write(Review review, List<ReviewImage> images, Map<Long, List<ReviewMark>> marksByImageId,
                                Path directory) {
        try {
            Files.createDirectories(directory);
            StringBuilder md = new StringBuilder("# Visual review: ").append(review.title()).append("\n\n");
            if (review.note() != null && !review.note().isBlank()) {
                md.append(review.note().strip()).append("\n\n");
            }
            int marked = 0;
            for (ReviewImage image : images) {
                List<ReviewMark> marks = marksByImageId.getOrDefault(image.id(), List.of());
                appendImage(md, image, marks, directory);
                marked += marks.size();
            }
            if (marked == 0) {
                md.append("The user marked nothing on these screenshots.\n\n");
            }
            md.append("Fix every numbered mark on every image (image <k> #<n>) in the code, then make new screenshots and call the "
                    + "cc-manager MCP tool submit_screenshots again.\n");
            Path markdown = directory.resolve(MARKDOWN_FILE).toAbsolutePath();
            Files.writeString(markdown, md, StandardCharsets.UTF_8);
            return new ReviewFeedback(markdown, prompt(review, markdown));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void appendImage(StringBuilder md, ReviewImage image, List<ReviewMark> marks, Path directory) {
        int imageNumber = image.position() + 1;
        md.append("## Image ").append(imageNumber).append(": ").append(image.label()).append("\n\n");
        md.append("- Original: ").append(image.fileName()).append("\n");
        if (marks.isEmpty()) {
            md.append("\nNo marks on this image.\n\n");
            return;
        }
        String annotated = "annotated-" + imageNumber + ".png";
        annotator.annotate(directory.resolve(image.fileName()), directory.resolve(annotated), marks);
        md.append("- Annotated: ").append(annotated).append("\n\n");
        for (int i = 0; i < marks.size(); i++) {
            appendMark(md, marks.get(i), i + 1, image);
        }
        md.append("\n");
    }

    private void appendMark(StringBuilder md, ReviewMark mark, int number, ReviewImage image) {
        boolean freeform = mark.kind() == MarkKind.FREEFORM;
        md.append("#").append(number).append(" ").append(mark.kind().name().toLowerCase(Locale.ROOT));
        if (freeform) {
            md.append(" with ").append(mark.points().size()).append(" points, bounding box x=").append(percent(mark.x())).append("% y=")
                    .append(percent(mark.y())).append("% w=").append(percent(mark.w())).append("% h=").append(percent(mark.h())).append("%");
        } else {
            md.append(" at x=").append(percent(mark.x())).append("%, y=").append(percent(mark.y())).append("%");
        }
        if (mark.kind() != MarkKind.PIN && !freeform) {
            md.append(" size w=").append(percent(mark.w())).append("% h=").append(percent(mark.h())).append("%");
        }
        md.append(" (px: ").append(px(mark.x(), image.width())).append(",").append(px(mark.y(), image.height()));
        if (mark.kind() != MarkKind.PIN) {
            md.append(",").append(px(mark.w(), image.width())).append(",").append(px(mark.h(), image.height()));
        }
        md.append(" of the original image)\n");
        if (freeform) {
            md.append("   points: ").append(vertexList(mark.points())).append("\n");
        }
        String comment = mark.comment() == null || mark.comment().isBlank() ? "(no comment)" : mark.comment().strip();
        md.append("   ").append(comment.replace("\n", "\n   ")).append("\n");
    }

    private static String vertexList(List<MarkPoint> points) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < Math.min(points.size(), MAX_LISTED_POINTS); i++) {
            list.append(i == 0 ? "" : " ").append("(").append(percent(points.get(i).x())).append(",").append(percent(points.get(i).y())).append(")");
        }
        return points.size() > MAX_LISTED_POINTS ? list + " ..." : list.toString();
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f", fraction * PERCENT);
    }

    private static long px(double fraction, int size) {
        return Math.round(fraction * size);
    }

    static String prompt(Review review, Path markdown) {
        String title = PromptText.oneLine(review.title() == null ? "" : review.title());
        if (title.length() > MAX_TITLE_CHARS) {
            title = title.substring(0, MAX_TITLE_CHARS).strip();
        }
        return "Visual review #" + review.id() + " \"" + title + "\": read \"" + PromptText.withoutHidden(markdown.toString())
                + "\" and fix every numbered mark on every image, then submit new screenshots with submit_screenshots.";
    }
}
