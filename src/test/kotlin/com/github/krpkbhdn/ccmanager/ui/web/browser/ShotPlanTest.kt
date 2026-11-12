package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ShotPlanTest {
    private val limits = ShotLimits(8192, 36_000_000, 20_000_000)

    private fun metrics(width: Int = 1000, height: Int, dpr: Double = 1.0, scrollY: Double = 0.0) =
        PageMetrics(0.0, scrollY, width, height, 1000, 700, dpr)

    @Test
    fun a_small_page_is_taken_whole_at_full_scale() {
        val plan = ShotPlan.of(ShotArea.WholePage, metrics(height = 2000), limits)

        assertThat(plan.scale).isEqualTo(1.0)
        assertThat(plan.cut).isFalse()
        assertThat(plan.beyondViewport).isTrue()
        assertThat(plan.clip).isEqualTo(ShotPlan.Clip(0.0, 0.0, 1000.0, 2000.0))
    }

    @Test
    fun a_tall_page_is_scaled_down_to_the_side_limit() {
        val plan = ShotPlan.of(ShotArea.WholePage, metrics(height = 12_000), limits)

        assertThat(plan.scale).isCloseTo(8192.0 / 12_000, within(1e-9))
        assertThat(plan.cut).isFalse()
        assertThat(plan.clip!!.height).isEqualTo(12_000.0)
    }

    @Test
    fun a_very_tall_page_is_cut_at_the_floor_scale() {
        val plan = ShotPlan.of(ShotArea.WholePage, metrics(height = 50_000), limits)

        assertThat(plan.scale).isEqualTo(0.5)
        assertThat(plan.cut).isTrue()
        assertThat(plan.clip!!.height * plan.scale).isLessThanOrEqualTo(8192.0)
        assertThat(plan.clip!!.height).isEqualTo(16_384.0)
        assertThat(plan.clip!!.width).isEqualTo(1000.0)
    }

    @Test
    fun the_device_scale_factor_counts_towards_the_limits() {
        val plan = ShotPlan.of(ShotArea.WholePage, metrics(height = 6000, dpr = 2.0), limits)

        assertThat(plan.scale).isCloseTo(8192.0 / 12_000, within(1e-9))
        assertThat(plan.clip!!.height * plan.scale * 2).isLessThanOrEqualTo(8192.0 + 1e-6)
    }

    @Test
    fun a_wide_page_is_scaled_by_its_width() {
        val plan = ShotPlan.of(ShotArea.WholePage, metrics(width = 10_000, height = 500), limits)

        assertThat(plan.scale).isCloseTo(0.8192, within(1e-9))
        assertThat(plan.cut).isFalse()
    }

    @Test
    fun the_pixel_limit_scales_a_large_page() {
        val plan = ShotPlan.of(ShotArea.WholePage, metrics(width = 8000, height = 8000), limits)

        assertThat(plan.scale).isCloseTo(0.75, within(1e-9))
    }

    @Test
    fun a_region_is_moved_by_the_scroll_offset_and_not_scaled() {
        val plan = ShotPlan.of(ShotArea.Region(10.0, 20.0, 300.0, 200.0), metrics(height = 4000, scrollY = 1000.0), limits)

        assertThat(plan.clip).isEqualTo(ShotPlan.Clip(10.0, 1020.0, 300.0, 200.0))
        assertThat(plan.scale).isEqualTo(1.0)
        assertThat(plan.beyondViewport).isFalse()
    }

    @Test
    fun the_visible_area_has_no_clip() {
        val plan = ShotPlan.of(ShotArea.Visible, metrics(height = 4000), limits)

        assertThat(plan).isEqualTo(ShotPlan(null, 1.0, false, false))
    }

    @Test
    fun the_rounded_output_stays_within_the_pixel_cap() {
        val plan = ShotPlan.of(ShotArea.WholePage, PageMetrics(0.0, 0.0, 6000, 7000, 1000, 700, 1.0), limits)

        val clip = plan.clip!!
        assertThat(Math.ceil(clip.width * plan.scale).toLong() * Math.ceil(clip.height * plan.scale).toLong()).isLessThanOrEqualTo(36_000_000L)
        assertThat(Math.round(clip.width * plan.scale) * Math.round(clip.height * plan.scale)).isLessThanOrEqualTo(36_000_000L)
        assertThat(plan.scale).isLessThan(0.93).isGreaterThan(0.9)
    }

    @Test
    fun a_container_whole_page_plan_covers_the_container_content_size() {
        val container = ScrollContainer(200.0, 50.0, 800.0, 600.0)
        val plan = ShotPlan.of(ShotArea.WholePage, PageMetrics(0.0, 0.0, 785, 3000, 1000, 700, 1.0, container), limits)

        assertThat(plan.clip).isEqualTo(ShotPlan.Clip(0.0, 0.0, 785.0, 3000.0))
        assertThat(plan.beyondViewport).isTrue()
    }

    @Test
    fun a_container_inside_the_viewport_is_usable() {
        assertThat(ScrollContainer(0.0, 56.0, 1000.0, 644.0).isUsableIn(1000, 700)).isTrue()
    }

    @Test
    fun a_container_sticking_out_of_the_viewport_or_tiny_is_not_usable() {
        assertThat(ScrollContainer(0.0, 0.0, 1000.0, 900.0).isUsableIn(1000, 700)).isFalse()
        assertThat(ScrollContainer(-50.0, 0.0, 500.0, 500.0).isUsableIn(1000, 700)).isFalse()
        assertThat(ScrollContainer(0.0, 0.0, 10.0, 500.0).isUsableIn(1000, 700)).isFalse()
    }

    @Test
    fun a_region_uses_the_scroll_of_its_selection_and_not_the_scroll_at_capture() {
        val plan = ShotPlan.of(ShotArea.Region(10.0, 20.0, 300.0, 200.0, PageScroll(0.0, 400.0)), metrics(height = 4000, scrollY = 1000.0), limits)

        assertThat(plan.clip).isEqualTo(ShotPlan.Clip(10.0, 420.0, 300.0, 200.0))
    }

    @Test
    fun a_2560_wide_page_up_to_twelve_thousand_px_is_taken_untouched_with_the_default_limits() {
        val defaults = ShotLimits(32_767, 64_000_000, 52_428_800)

        for (dpr in listOf(1.0, 1.25)) {
            val plan = ShotPlan.of(ShotArea.WholePage, PageMetrics(0.0, 0.0, 2560, 12_000, 2560, 1080, dpr), defaults)
            assertThat(plan.scale).describedAs("dpr $dpr").isEqualTo(1.0)
            assertThat(plan.cut).isFalse()
        }
    }

    @Test
    fun a_2560_wide_page_at_double_density_is_reduced_to_the_biggest_scale_that_fits() {
        val plan = ShotPlan.of(ShotArea.WholePage, PageMetrics(0.0, 0.0, 2560, 12_000, 2560, 1080, 2.0), ShotLimits(32_767, 64_000_000, 52_428_800))

        assertThat(plan.scale).isGreaterThan(0.7).isLessThan(0.75)
        assertThat(plan.scale * 2).describedAs("still above device scale 1").isGreaterThan(1.0)
    }
}
