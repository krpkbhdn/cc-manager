package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.ReviewService.CaptureTarget
import com.github.krpkbhdn.ccmanager.ui.await
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class WebCaptureTargetsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reviews = mock(ReviewService::class.java)
    private var changed = Runnable { }
    private val errors = mutableListOf<String>()

    init {
        `when`(reviews.subscribe(any())).thenAnswer { changed = it.getArgument(0); Runnable { } }
    }

    private val targets = WebCaptureTargets(reviews, scope) { errors += it }

    @AfterEach
    fun stop() = scope.cancel()

    private fun target(id: Long, images: Int, max: Int = 20) = CaptureTarget(id, "r$id", Instant.EPOCH, images, max)

    private fun load(vararg list: CaptureTarget) {
        `when`(reviews.captureTargets(1)).thenReturn(list.toList())
        targets.targets(1)
        await { targets.targets(1).size == list.size }
    }

    @Test
    fun `targets follow the service and refresh on a change notification`() {
        load(target(1, 2))
        `when`(reviews.captureTargets(1)).thenReturn(listOf(target(2, 0), target(1, 2)))

        changed.run()

        await { targets.targets(1).size == 2 }
        assertThat(targets.targets(1).map { it.id() }).containsExactly(2L, 1L)
    }

    @Test
    fun `a pin is stale when its review left the list or is full, and not otherwise`() {
        load(target(1, 2), target(2, 20))

        assertThat(targets.stale(1)).isFalse()
        targets.pin(1, 1)
        assertThat(targets.stale(1)).isFalse()
        targets.pin(1, 2)
        assertThat(targets.stale(1)).isTrue()
        targets.pin(1, 9)
        assertThat(targets.stale(1)).isTrue()
        targets.unpin(1)
        assertThat(targets.stale(1)).isFalse()
        assertThat(targets.pinned[1]).isNull()
    }
}
