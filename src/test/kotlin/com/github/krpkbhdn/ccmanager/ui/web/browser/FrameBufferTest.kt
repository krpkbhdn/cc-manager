package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class FrameBufferTest {
    private fun bgra(vararg pixels: List<Int>): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(pixels.size * 4)
        pixels.forEach { p -> p.forEach { buffer.put(it.toByte()) } }
        buffer.flip()
        return buffer
    }

    private fun argb(frame: BrowserFrame): IntArray =
        IntArray(frame.widthPx * frame.heightPx).also { frame.bitmap.readPixels(it) }

    @Test
    fun converts_bgra_bytes_to_argb_pixels() {
        val frame = FrameBuffer().publish(bgra(listOf(0, 0, 255, 255), listOf(0, 255, 0, 255)), 2, 1)!!

        assertThat(argb(frame)).containsExactly(0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        assertThat(frame.widthPx).isEqualTo(2)
        assertThat(frame.heightPx).isEqualTo(1)
    }

    @Test
    fun keeps_the_alpha_of_premultiplied_pixels() {
        val frame = FrameBuffer().publish(bgra(listOf(0, 0, 128, 128)), 1, 1)!!

        val pixel = argb(frame)[0]
        assertThat(pixel ushr 24).isEqualTo(128)
        assertThat((pixel shr 16) and 0xFF).isBetween(254, 255)
    }

    @Test
    fun version_grows_with_every_publish() {
        val buffer = FrameBuffer()

        val first = buffer.publish(bgra(listOf(1, 2, 3, 255)), 1, 1)!!
        val second = buffer.publish(bgra(listOf(1, 2, 3, 255)), 1, 1)!!

        assertThat(second.version).isGreaterThan(first.version)
    }

    @Test
    fun restaging_for_the_next_frame_does_not_change_an_earlier_published_frame() {
        val buffer = FrameBuffer()
        val first = buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)!!

        buffer.publish(bgra(listOf(255, 0, 0, 255)), 1, 1)

        assertThat(argb(first)[0]).isEqualTo(0xFFFF0000.toInt())
    }

    @Test
    fun a_published_frame_stays_drawable_within_the_frame_window() {
        val buffer = FrameBuffer()
        val old = buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)!!

        repeat(29) { buffer.publish(bgra(listOf(1, 1, 1, 255)), 1, 1) }

        assertThat(argb(old)[0]).isEqualTo(0xFFFF0000.toInt())
    }

    @Test
    fun a_leased_image_stays_open_past_the_window_and_closes_on_release() {
        val buffer = FrameBuffer()
        buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)
        var leased: org.jetbrains.skia.Image? = null

        buffer.withLatestImage { image, _ ->
            leased = image
            repeat(40) { buffer.publish(bgra(listOf(1, 1, 1, 255)), 1, 1) }
            assertThat(leased!!.isClosed).isFalse()
            assertThat(buffer.openImages).isEqualTo(30)
        }

        assertThat(leased!!.isClosed).isTrue()
    }

    @Test
    fun close_during_a_lease_defers_closing_that_image() {
        val buffer = FrameBuffer()
        buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)

        buffer.withLatestImage { image, _ ->
            buffer.close()
            assertThat(image!!.isClosed).isFalse()
            assertThat(FrameCrop.png(image, 1.0, 0, 0, 1, 1)).isNotNull
        }
    }

    @Test
    fun the_latest_image_carries_the_density_of_its_paint() {
        val buffer = FrameBuffer()
        buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1, 2.625)

        assertThat(buffer.withLatestImage { _, density -> density }).isEqualTo(2.625)
    }

    @Test
    fun images_older_than_the_frame_window_are_closed() {
        val buffer = FrameBuffer()

        repeat(100) { buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1) }

        assertThat(buffer.openImages).isEqualTo(30)
    }

    @Test
    fun retained_bytes_are_bounded_but_the_newest_image_stays_alone() {
        val buffer = FrameBuffer(maxRetainedBytes = 1)

        repeat(10) { buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1) }
        val newest = buffer.publish(bgra(listOf(255, 0, 0, 255)), 1, 1)!!

        assertThat(buffer.openImages).isEqualTo(1)
        assertThat(argb(newest)[0]).isEqualTo(0xFF0000FF.toInt())
    }

    @Test
    fun close_releases_every_image() {
        val buffer = FrameBuffer()
        repeat(3) { buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1) }

        buffer.close()

        assertThat(buffer.openImages).isZero()
    }

    @Test
    fun stage_and_publish_after_close_do_nothing() {
        val buffer = FrameBuffer()
        buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)

        buffer.close()
        buffer.close()
        buffer.stage(bgra(listOf(0, 0, 255, 255)), 1, 1)

        assertThat(buffer.flush()).isNull()
        assertThat(buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)).isNull()
        assertThat(buffer.withLatestImage { image, _ -> image }).isNull()
    }

    @Test
    fun flush_without_a_new_paint_publishes_nothing() {
        val buffer = FrameBuffer()
        buffer.stage(bgra(listOf(0, 0, 255, 255)), 1, 1)

        assertThat(buffer.flush()).isNotNull
        assertThat(buffer.flush()).isNull()
    }

    @Test
    fun handles_a_size_change_between_frames() {
        val buffer = FrameBuffer()
        buffer.publish(bgra(listOf(0, 0, 255, 255)), 1, 1)

        val bigger = buffer.publish(bgra(listOf(1, 1, 1, 255), listOf(2, 2, 2, 255)), 2, 1)!!

        assertThat(argb(bigger)).hasSize(2)
    }
}
