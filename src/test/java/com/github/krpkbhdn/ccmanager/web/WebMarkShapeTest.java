package com.github.krpkbhdn.ccmanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.review.MarkKind;
import com.github.krpkbhdn.ccmanager.review.MarkPoint;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebMarkShapeTest {

    @Test
    void jsonRoundTrip() {
        var shape = new WebMarkShape(MarkKind.FREEFORM, 1, 2, 30, 40, List.of(new MarkPoint(1, 2), new MarkPoint(31, 2), new MarkPoint(15, 42)));

        assertThat(WebMarkShape.fromJson(shape.toJson())).isEqualTo(shape);
        assertThat(WebMarkShape.fromJson("{\"kind\":\"PIN\",\"x\":5,\"y\":6,\"w\":0,\"h\":0}").points()).isEmpty();
    }

    @Test
    void hostileJsonIsRejected() {
        var hostile = List.of(
                "{\"kind\":\"RECTANGLE\",\"x\":1e7,\"y\":0,\"w\":1,\"h\":1}",
                "{\"kind\":\"RECTANGLE\",\"x\":0,\"y\":-1000001,\"w\":1,\"h\":1}",
                "{\"kind\":\"RECTANGLE\",\"x\":NaN,\"y\":0,\"w\":1,\"h\":1}",
                "{\"kind\":\"RECTANGLE\",\"x\":0,\"y\":0,\"w\":-1,\"h\":1}",
                "{\"kind\":\"CIRCLE\",\"x\":0,\"y\":0,\"w\":1,\"h\":1}",
                "{\"x\":0,\"y\":0,\"w\":1,\"h\":1}",
                "{\"kind\":\"RECTANGLE\",\"x\":0,\"y\":0,\"w\":1,\"h\":1,\"points\":[{\"x\":0,\"y\":0}]}",
                "{\"kind\":\"FREEFORM\",\"x\":0,\"y\":0,\"w\":1,\"h\":1,\"points\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":1}]}",
                "{\"kind\":\"RECTANGLE\",\"x\":0,\"y\":0,\"w\":1,\"h\":1,\"extra\":1}",
                "not json");

        hostile.forEach(json -> assertThatThrownBy(() -> WebMarkShape.fromJson(json)).as(json).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void rejectsTooManyPointsAndNonFiniteConstruction() {
        var many = Collections.nCopies(WebMarkShape.MAX_POINTS + 1, new MarkPoint(1, 1));

        assertThatThrownBy(() -> new WebMarkShape(MarkKind.FREEFORM, 0, 0, 1, 1, many)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebMarkShape(MarkKind.RECTANGLE, Double.POSITIVE_INFINITY, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebMarkShape(MarkKind.FREEFORM, 0, 0, 1, 1,
                List.of(new MarkPoint(0, 0), new MarkPoint(1, 1), new MarkPoint(Double.NaN, 1)))).isInstanceOf(IllegalArgumentException.class);
    }
}
