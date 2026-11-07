package com.github.krpkbhdn.ccmanager.usage.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CostDeltaTest {

    @Test
    void firstSampleCountsWholeCost() {
        assertThat(CostDelta.of(OptionalDouble.empty(), 1.5)).isEqualTo(1.5);
    }

    @Test
    void increaseCountsTheDifference() {
        assertThat(CostDelta.of(OptionalDouble.of(1.0), 1.75)).isEqualTo(0.75);
    }

    @Test
    void dropStartsNewBaseline() {
        assertThat(CostDelta.of(OptionalDouble.of(3.0), 0.5)).isEqualTo(0.5);
    }

    @Test
    void resumedSessionWithSameCostAddsNothing() {
        assertThat(CostDelta.of(OptionalDouble.of(2.0), 2.0)).isZero();
    }
}
