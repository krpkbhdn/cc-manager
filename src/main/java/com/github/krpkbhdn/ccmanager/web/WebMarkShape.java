package com.github.krpkbhdn.ccmanager.web;

import com.github.krpkbhdn.ccmanager.review.MarkKind;
import com.github.krpkbhdn.ccmanager.review.MarkPoint;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * A mark drawn on the page, in document CSS px (or crop px for a screenshot). For FREEFORM x, y, w, h are the bounding box of
 * {@code points} (at least 3); other kinds have no points. Invalid values are rejected on construction, so a parsed shape is always safe.
 */
public record WebMarkShape(MarkKind kind, double x, double y, double w, double h, List<MarkPoint> points) {

    public static final double MAX_ABS = 1e6;
    public static final int MAX_POINTS = 500;
    private static final int MIN_POLYGON_POINTS = 3;
    private static final JsonMapper JSON = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public WebMarkShape {
        if (kind == null) {
            throw new IllegalArgumentException("A shape needs a kind.");
        }
        points = points == null ? List.of() : List.copyOf(points);
        requireInRange(x, y, w, h);
        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("A shape cannot have a negative size.");
        }
        if (kind == MarkKind.FREEFORM) {
            if (points.size() < MIN_POLYGON_POINTS || points.size() > MAX_POINTS) {
                throw new IllegalArgumentException("A freeform shape needs " + MIN_POLYGON_POINTS + " to " + MAX_POINTS + " points.");
            }
            points.forEach(point -> requireInRange(point.x(), point.y()));
        } else if (!points.isEmpty()) {
            throw new IllegalArgumentException("Only a freeform shape has points.");
        }
    }

    public WebMarkShape(MarkKind kind, double x, double y, double w, double h) {
        this(kind, x, y, w, h, List.of());
    }

    /** @throws IllegalArgumentException for malformed JSON or a shape that fails the validation */
    public static WebMarkShape fromJson(String json) {
        try {
            return JSON.readValue(json, WebMarkShape.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid shape: " + e.getClass().getSimpleName(), e);
        }
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    private static void requireInRange(double... values) {
        for (var value : values) {
            if (!Double.isFinite(value) || Math.abs(value) > MAX_ABS) {
                throw new IllegalArgumentException("A shape coordinate is not a finite number within +-" + MAX_ABS + ".");
            }
        }
    }
}
