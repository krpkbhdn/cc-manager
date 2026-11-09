package com.github.krpkbhdn.ccmanager.review;

import java.util.List;

/** Coordinates are normalised 0..1 of the image; a PIN has w = h = 0; a FREEFORM carries its polygon in points (x, y, w, h are then recomputed on save). */
public record MarkDraft(MarkKind kind, double x, double y, double w, double h, String comment, List<MarkPoint> points) {

    public MarkDraft {
        points = points == null ? List.of() : List.copyOf(points);
    }

    public MarkDraft(MarkKind kind, double x, double y, double w, double h, String comment) {
        this(kind, x, y, w, h, comment, List.of());
    }
}
