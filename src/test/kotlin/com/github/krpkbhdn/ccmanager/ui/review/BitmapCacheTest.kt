package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.review.ReviewImage
import com.github.krpkbhdn.ccmanager.ui.await
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class BitmapCacheTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val image = ReviewImage(10, 1, 0, "a", "a.png", 1, 1)

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `a decode finishing after the picture was dropped is not stored`() {
        val release = CountDownLatch(1)
        val started = AtomicBoolean()
        val decoded = AtomicBoolean()
        val cache = BitmapCache(scope, 3, { started.set(true); release.await(); decoded.set(true); ImageBitmap(1, 1) })
        cache.get(image)
        await { started.get() }

        cache.drop(image.id)
        release.countDown()
        await { decoded.get() }
        Thread.sleep(100)

        assertThat(cache.get(image)).isNull()
    }
}
