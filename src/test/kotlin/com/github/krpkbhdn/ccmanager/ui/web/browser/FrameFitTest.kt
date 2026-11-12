package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FrameFitTest {
    @Test
    fun fractional_densities_round_within_the_tolerance() {
        assertThat(frameFits(1079, 2403, 411, 915, 2.625)).isTrue()
        assertThat(frameFits(500, 450, 333, 300, 1.5)).isTrue()
    }

    @Test
    fun a_device_scale_below_one_fits_its_scaled_size() {
        assertThat(frameFits(401, 150, 801, 301, 0.5)).isTrue()
    }

    @Test
    fun a_frame_of_the_previous_size_or_density_does_not_fit() {
        assertThat(frameFits(400, 300, 300, 400, 1.0)).isFalse()
        assertThat(frameFits(400, 300, 400, 300, 2.0)).isFalse()
    }
}
