package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.time.Duration

class FramePublisherTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val published = mutableListOf<BrowserFrame>()
    private var clock = 0L
    private val publisher = FramePublisher(FrameBuffer(), { synchronized(published) { published += it } }, scheduler, INTERVAL, { clock })

    @AfterEach
    fun stop() {
        scheduler.shutdownNow()
    }

    private fun paint(blue: Int) = publisher.onPaint(ByteBuffer.wrap(byteArrayOf(blue.toByte(), 0, 0, -1)), 1, 1)

    private fun awaitPublished(count: Int) {
        await().atMost(Duration.ofSeconds(5)).until { synchronized(published) { published.size } >= count }
    }

    @Test
    fun the_first_paint_goes_out_at_once() {
        paint(1)

        assertThat(published).hasSize(1)
    }

    @Test
    fun paints_inside_the_interval_are_coalesced_and_the_last_one_still_goes_out() {
        paint(1)
        clock += 1
        repeat(20) { paint(it + 10) }
        assertThat(published).hasSize(1)

        clock += INTERVAL
        awaitPublished(2)

        assertThat(published).hasSize(2)
        val pixel = IntArray(1).also { published.last().bitmap.readPixels(it) }[0]
        assertThat(pixel and 0xFF).isEqualTo(29)
    }

    @Test
    fun nothing_is_published_while_hidden() {
        publisher.hidden = true

        paint(1)

        assertThat(published).isEmpty()
    }

    private companion object {
        const val INTERVAL = 50_000_000L
    }
}
