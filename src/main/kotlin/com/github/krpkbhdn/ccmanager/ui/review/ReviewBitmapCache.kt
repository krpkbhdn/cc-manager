package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.review.ReviewImage
import com.github.krpkbhdn.ccmanager.ui.state.io
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val MAX_FAILURES = 200

/** Decoded pictures, at most [limit] kept (oldest dropped); a picture that failed once is not decoded again. */
internal class BitmapCache(private val scope: CoroutineScope, private val limit: Int, private val decoder: (ReviewImage) -> ImageBitmap) {
    private val store = mutableStateMapOf<Long, ImageBitmap>()
    private val failures = mutableStateMapOf<Long, Boolean>()
    private val order = ArrayDeque<Long>()
    private val loading = ConcurrentHashMap.newKeySet<Long>()

    fun get(image: ReviewImage): ImageBitmap? {
        store[image.id]?.let { return it }
        if (failures.containsKey(image.id)) return null
        if (loading.add(image.id)) scope.launch {
            try {
                val decoded = decodeOrNull(image)
                if (decoded != null) put(image.id, decoded) else fail(image.id)
            } finally {
                loading.remove(image.id)
            }
        }
        return null
    }

    fun failed(id: Long): Boolean = failures.containsKey(id)

    /** shortcut: past [MAX_FAILURES] all remembered failures are forgotten (a bad picture is retried once), no per-entry age. */
    @Synchronized
    private fun fail(id: Long) {
        if (failures.size >= MAX_FAILURES) failures.clear()
        failures[id] = true
    }

    @Synchronized
    fun drop(id: Long) {
        store.remove(id)
        failures.remove(id)
        order.remove(id)
    }

    private suspend fun decodeOrNull(image: ReviewImage): ImageBitmap? = try {
        io { decoder(image) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    @Synchronized
    private fun put(id: Long, bitmap: ImageBitmap) {
        store[id] = bitmap
        order.remove(id)
        order.addLast(id)
        while (order.size > limit) store.remove(order.removeFirst())
    }
}
