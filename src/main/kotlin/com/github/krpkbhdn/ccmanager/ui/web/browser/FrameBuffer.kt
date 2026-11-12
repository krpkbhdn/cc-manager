package com.github.krpkbhdn.ccmanager.ui.web.browser

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Turns CEF paint buffers into frames. CEF hands BGRA with premultiplied alpha that is valid only during the paint
 * call, so [stage] copies it once straight into the pixel memory of a Skia bitmap (reused while unflushed) and
 * [flush] wraps that bitmap, made immutable so Skia shares the pixels instead of copying them again.
 * Skia images hold native memory the GC does not see, so an image is closed once it is [KEEP_FRAMES] published frames
 * old (the UI cannot be that stale) or when the retained bytes exceed [maxRetainedBytes]; the newest one stays open.
 * An image another thread is using through [withLatestImage] is leased: eviction only marks it and the last release closes it.
 */
class FrameBuffer(private val maxRetainedBytes: Long = DEFAULT_MAX_RETAINED_BYTES) {
    private class Retained(val image: Image, val bytes: Long, val version: Long, val density: Double) {
        var leases = 0
        var evicted = false
    }

    private val versions = AtomicLong()
    private val retained = ArrayDeque<Retained>()
    private var retainedBytes = 0L
    private var staging: Bitmap? = null
    private var stagedDensity = 1.0
    private var closed = false

    @Synchronized
    fun stage(buffer: ByteBuffer, width: Int, height: Int, density: Double = 1.0) {
        if (closed) return
        stagedDensity = density
        val size = width * height * BYTES_PER_PIXEL
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val target = staging?.takeIf { it.width == width && it.height == height } ?: Bitmap().let {
            staging?.close()
            staging = null
            if (!it.allocPixels(info)) {
                it.close()
                error("Cannot allocate ${width}x$height pixels")
            }
            it.also { staging = it }
        }
        target.peekPixels()!!.use { pixels ->
            MemorySegment.ofAddress(pixels.addr).reinterpret(size.toLong())
                .copyFrom(MemorySegment.ofBuffer(buffer).asSlice(0, size.toLong()))
        }
    }

    /** The frame of the latest staged paint, or null if nothing new was staged since the last flush. */
    @Synchronized
    fun flush(): BrowserFrame? {
        val bitmap = staging ?: return null
        staging = null
        val width = bitmap.width
        val height = bitmap.height
        val image = Image.makeFromBitmap(bitmap.setImmutable())
        bitmap.close()
        val version = versions.incrementAndGet()
        val density = stagedDensity
        retained.addLast(Retained(image, width.toLong() * height * BYTES_PER_PIXEL, version, density))
        retainedBytes += retained.last().bytes
        while (retained.size > 1 && (version - retained.first().version >= KEEP_FRAMES || retainedBytes > maxRetainedBytes)) {
            retainedBytes -= retained.first().bytes
            evict(retained.removeFirst())
        }
        return BrowserFrame(image.toComposeImageBitmap(), width, height, version, density)
    }

    /** Stages and flushes one paint; null once the buffer is closed. */
    fun publish(buffer: ByteBuffer, width: Int, height: Int, density: Double = 1.0): BrowserFrame? {
        stage(buffer, width, height, density)
        return flush()
    }

    /** Runs [block] with the newest image and its density (null before the first frame) under a lease; it must not keep the image. */
    fun <T> withLatestImage(block: (Image?, Double) -> T): T {
        val lease = synchronized(this) { retained.lastOrNull()?.also { it.leases++ } }
        try {
            return block(lease?.image, lease?.density ?: 1.0)
        } finally {
            if (lease != null) synchronized(this) { if (--lease.leases == 0 && lease.evicted) lease.image.close() }
        }
    }

    private fun evict(entry: Retained) {
        entry.evicted = true
        if (entry.leases == 0) entry.image.close()
    }

    /** Images still open, for memory tests. */
    @get:Synchronized
    val openImages: Int get() = retained.size

    /** Closes every image, so frames handed out earlier must no longer be drawn; later paints are ignored. Safe to call twice. */
    @Synchronized
    fun close() {
        closed = true
        retained.forEach(::evict)
        retained.clear()
        retainedBytes = 0
        staging?.close()
        staging = null
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
        const val KEEP_FRAMES = 30
        const val DEFAULT_MAX_RETAINED_BYTES = 256L * 1024 * 1024
    }
}
