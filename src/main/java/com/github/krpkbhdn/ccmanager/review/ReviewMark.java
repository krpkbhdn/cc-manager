package com.github.krpkbhdn.ccmanager.review;

import java.util.List;

/** For a FREEFORM x, y, w, h are the bounding box of points (at least 3); other kinds have no points. */
public record ReviewMark(long id, long imageId, MarkKind kind, double x, double y, double w, double h, String comment, List<MarkPoint> points) {

    public ReviewMark {
        points = points == null ? List.of() : List.copyOf(points);
    }

    public ReviewMark(long id, long imageId, MarkKind kind, double x, double y, double w, double h, String comment) {
        this(id, imageId, kind, x, y, w, h, comment, List.of());
    }
}
