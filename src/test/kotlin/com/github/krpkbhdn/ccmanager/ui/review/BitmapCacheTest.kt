package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.review.ReviewImage
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class BitmapCacheTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val decodes = AtomicInteger()

    @AfterEach
    fun stop() = scope.cancel()

    private fun image(id: Long) = ReviewImage(id, 1, 0, "l", "f", 1, 1)

    private fun cache(limit: Int, broken: Set<Long> = emptySet()) = BitmapCache(scope, limit) {
        decodes.incrementAndGet()
        if (it.id in broken) error("cannot decode")
        ImageBitmap(1, 1)
    }

    private fun BitmapCache.load(id: Long): ImageBitmap? {
        repeat(200) {
            get(image(id))?.let { return it }
            if (failed(id)) return null
            Thread.sleep(10)
        }
        error("not settled: $id")
    }

    @Test
    fun `keeps at most the limit and drops the oldest picture`() {
        val cache = cache(2)
        cache.load(1)
        cache.load(2)
        cache.load(3)

        val before = decodes.get()
        assertThat(cache.get(image(3))).isNotNull
        assertThat(cache.get(image(2))).isNotNull
        assertThat(decodes.get()).isEqualTo(before)
        assertThat(cache.get(image(1))).isNull()
    }

    @Test
    fun `a failed picture is not decoded again until dropped`() {
        val cache = cache(2, broken = setOf(1L))
        assertThat(cache.load(1)).isNull()
        val after = decodes.get()

        assertThat(cache.get(image(1))).isNull()
        assertThat(decodes.get()).isEqualTo(after)
        cache.drop(1)
        assertThat(cache.failed(1)).isFalse()
    }

    @Test
    fun `remembered failures are bounded`() {
        val ids = (1L..MAX_FAILURES + 1L).toSet()
        val cache = cache(2, broken = ids)
        ids.forEach { cache.load(it) }

        assertThat(ids.count { cache.failed(it) }).isLessThanOrEqualTo(MAX_FAILURES)
    }
}
