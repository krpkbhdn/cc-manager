package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.Review
import com.github.krpkbhdn.ccmanager.review.ReviewImage
import com.github.krpkbhdn.ccmanager.review.ReviewService
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

data class SessionChoice(val ccmSessionId: String, val label: String)

data class SendTarget(val review: Review, val candidates: List<SessionChoice>)

private const val SAVE_DEBOUNCE_MILLIS = 400L
private const val FULL_CACHE_SIZE = 3
private const val THUMBNAIL_CACHE_SIZE = 40
private const val THUMBNAIL_MAX_SIDE = 256f
private const val DISPOSE_FLUSH_TIMEOUT_MILLIS = 2000L
private const val START_FAILED = "Could not start a session"

/**
 * Visual review screen state: reviews per project, decoded screenshots, marks (saved debounced, numbered per image) and sending the feedback to a session.
 * Service calls run off the UI thread.
 */
class ReviewState(
    private val service: ReviewService,
    private val live: () -> LiveSessions,
    private val enqueue: (ccmSessionId: String, text: String) -> Unit,
    private val startSession: (projectId: Long, prompt: String, onNotStarted: () -> Unit, onStarted: (String) -> Unit) -> Unit,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    private val tools = ReviewToolState()

    /** The toolbar's sticky default tool. */
    var tool: ReviewTool
        get() = tools.tool
        set(value) { tools.tool = value }
    val oneShotTool: ReviewTool? get() = tools.oneShotTool
    internal val draft: PolygonDraft get() = tools.draft
    var selectedMark: Int?
        get() = tools.selectedMark
        set(value) { tools.selectedMark = value }
    var sendTarget by mutableStateOf<SendTarget?>(null)
        private set

    private val reviewsByProject = mutableStateMapOf<Long, List<Review>>()
    private val selection = mutableStateMapOf<Long, Pair<Long, Int>>()
    private val imagesByReview = mutableStateMapOf<Long, List<ReviewImage>>()
    private val fullImages = BitmapCache(scope, FULL_CACHE_SIZE, ::decode)
    private val thumbnails = BitmapCache(scope, THUMBNAIL_CACHE_SIZE, ::decodeThumbnail)
    private val loadingImages = ConcurrentHashMap.newKeySet<Long>()
    private val markLists = ConcurrentHashMap<Long, SnapshotStateList<MarkDraft>>()
    private val dirty = ConcurrentHashMap.newKeySet<Long>()
    private val saveJobs = ConcurrentHashMap<Long, Job>()
    private val writeLock = Mutex()
    private val watchedProjects = ConcurrentHashMap.newKeySet<Long>()
    private var unsubscribe: Runnable? = null
    private val reload = Refresher(scope) {
        watchedProjects.toList().forEach { projectId ->
            runGuarded(reportError) {
                val fresh = io { service.list(projectId) }.sortedWith(compareByDescending<Review> { it.createdAt }.thenByDescending { it.id })
                val gone = reviewsByProject[projectId].orEmpty().map { it.id } - fresh.map { it.id }.toSet()
                reviewsByProject[projectId] = fresh
                gone.forEach(::forgetReview)
            }
        }
    }

    fun start() {
        unsubscribe = service.subscribe(Runnable { reload.request() })
        reload.request()
    }

    /** Writes pending marks before the window goes away; waits at most [DISPOSE_FLUSH_TIMEOUT_MILLIS]. */
    fun dispose() {
        unsubscribe?.run()
        unsubscribe = null
        reload.close()
        val finalFlush = CoroutineScope(Dispatchers.IO).launch { runGuarded(reportError) { flush() } }
        runBlocking { withTimeoutOrNull(DISPOSE_FLUSH_TIMEOUT_MILLIS) { finalFlush.join() } }
    }

    fun reviews(projectId: Long): List<Review> {
        if (watchedProjects.add(projectId)) reload.request()
        return reviewsByProject[projectId].orEmpty()
    }

    fun pendingCount(projectId: Long): Int = reviews(projectId).count { it.status == ReviewStatus.PENDING }

    fun selectedReview(projectId: Long): Review? {
        val all = reviews(projectId)
        val chosen = selection[projectId]?.first
        return all.firstOrNull { it.id == chosen } ?: all.firstOrNull { it.status == ReviewStatus.PENDING } ?: all.firstOrNull()
    }

    fun selectedImageIndex(projectId: Long): Int = selection[projectId]?.takeIf { it.first == selectedReview(projectId)?.id }?.second ?: 0

    val effectiveTool: ReviewTool get() = tools.effectiveTool

    /** The next gesture draws exactly one element of [kind], then the default tool is back. */
    fun arm(kind: ReviewTool) = tools.arm(kind)

    fun clearOneShot() = tools.clearOneShot()

    /** Esc: drops an armed one-shot first, then the selection; false when there was nothing to drop. */
    fun cancel(): Boolean = tools.cancel()

    fun chooseTool(chosen: ReviewTool) = tools.chooseTool(chosen)

    fun select(projectId: Long, reviewId: Long, imageIndex: Int = 0) {
        selection[projectId] = reviewId to imageIndex
        selectedMark = null
        scope.launch { runGuarded(reportError) { flush() } }
    }

    fun images(reviewId: Long): List<ReviewImage> {
        imagesByReview[reviewId]?.let { return it }
        if (loadingImages.add(reviewId)) scope.launch {
            runGuarded(reportError) { imagesByReview[reviewId] = io { service.images(reviewId) } }
            loadingImages.remove(reviewId)
        }
        return emptyList()
    }

    /** Full-size picture for the viewer; null while decoding or when it cannot be shown (see [cannotShow]). */
    fun bitmap(image: ReviewImage): ImageBitmap? = fullImages.get(image)

    fun thumbnail(image: ReviewImage): ImageBitmap? = thumbnails.get(image)

    fun cannotShow(image: ReviewImage): Boolean = fullImages.failed(image.id)

    private fun decode(image: ReviewImage): ImageBitmap = Image.makeFromEncoded(Files.readAllBytes(service.imagePath(image))).toComposeImageBitmap()

    private fun decodeThumbnail(image: ReviewImage): ImageBitmap {
        val full = Image.makeFromEncoded(Files.readAllBytes(service.imagePath(image)))
        try {
            val scale = min(1f, THUMBNAIL_MAX_SIDE / max(full.width, full.height))
            val width = max(1, (full.width * scale).roundToInt())
            val height = max(1, (full.height * scale).roundToInt())
            Surface.makeRasterN32Premul(width, height).use { surface ->
                val source = Rect.makeWH(full.width.toFloat(), full.height.toFloat())
                surface.canvas.drawImageRect(full, source, Rect.makeWH(width.toFloat(), height.toFloat()), FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
                return surface.makeImageSnapshot().toComposeImageBitmap()
            }
        } finally {
            full.close()
        }
    }

    private fun forgetReview(reviewId: Long) {
        imagesByReview.remove(reviewId)?.forEach { image ->
            fullImages.drop(image.id)
            thumbnails.drop(image.id)
            markLists.remove(image.id)
            dirty.remove(image.id)
            saveJobs.remove(image.id)?.cancel()
        }
    }

    fun marks(imageId: Long): SnapshotStateList<MarkDraft> = markLists.computeIfAbsent(imageId) {
        mutableStateListOf<MarkDraft>().also { list ->
            scope.launch { runGuarded(reportError) { list.addAll(0, io { service.marks(imageId) }.map { MarkDraft(it.kind, it.x, it.y, it.w, it.h, it.comment, it.points) }) } }
        }
    }

    fun addMark(imageId: Long, mark: MarkDraft) {
        val list = marks(imageId)
        list.add(mark)
        tools.clearOneShot()
        selectedMark = list.lastIndex
        persist(imageId)
    }

    /** Closes the polygon draft into a mark of [imageId]; false (draft kept) when it has too few points or no area. */
    internal fun closeDraft(imageId: Long, imageW: Float, imageH: Float): Boolean {
        val mark = tools.draft.close(imageW, imageH) ?: return false
        addMark(imageId, mark)
        return true
    }

    fun setComment(imageId: Long, index: Int, text: String) {
        val list = marks(imageId)
        if (index !in list.indices) return
        val old = list[index]
        list[index] = MarkDraft(old.kind, old.x, old.y, old.w, old.h, text, old.points)
        persist(imageId)
    }

    /** Live edit of a mark's geometry; the comment stays and saving is debounced like every mark change. */
    fun updateMark(imageId: Long, index: Int, edited: MarkDraft) {
        val list = marks(imageId)
        if (index !in list.indices) return
        list[index] = MarkDraft(edited.kind, edited.x, edited.y, edited.w, edited.h, list[index].comment, edited.points)
        persist(imageId)
    }

    fun deleteMark(imageId: Long, index: Int) {
        val list = marks(imageId)
        if (index !in list.indices) return
        list.removeAt(index)
        selectedMark = null
        persist(imageId)
    }

    private fun persist(imageId: Long) {
        dirty += imageId
        saveJobs[imageId]?.cancel()
        saveJobs[imageId] = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            runGuarded(reportError) { writeIfDirty(imageId) }
        }
    }

    /** Never cancelled once started; the dirty flag stays set until the write succeeded. */
    private suspend fun writeIfDirty(imageId: Long) = withContext(NonCancellable) {
        writeLock.withLock {
            if (!dirty.remove(imageId)) return@withLock
            val snapshot = marks(imageId).toList()
            var written = false
            try {
                io { service.saveMarks(imageId, snapshot) }
                written = true
            } finally {
                if (!written) dirty += imageId
            }
        }
    }

    /** Waits for running writes, then writes every remaining dirty image; the first failure is rethrown after all were tried. */
    private suspend fun flush() {
        saveJobs.values.forEach { it.cancel() }
        saveJobs.clear()
        var failure: RuntimeException? = null
        dirty.toList().forEach { id ->
            try {
                writeIfDirty(id)
            } catch (e: RuntimeException) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
    }

    fun send(review: Review) {
        scope.launch {
            runGuarded(reportError) {
                val bound = review.ccmSessionId
                if (bound == null) offerTargets(review) else deliver(review, bound)
            }
        }
    }

    fun sendTo(review: Review, ccmSessionId: String?) {
        scope.launch {
            runGuarded(reportError) {
                sendTarget = null
                deliver(review, ccmSessionId)
            }
        }
    }

    fun dismissSendTarget() {
        sendTarget = null
    }

    private fun isLive(ccmSessionId: String): Boolean = live().find(ccmSessionId) != null

    private fun candidates(projectId: Long): List<SessionChoice> =
        live().of(projectId).map { SessionChoice(it.ccmSessionId, it.title?.takeIf(String::isNotBlank) ?: it.ccmSessionId) }

    private fun offerTargets(review: Review) {
        sendTarget = SendTarget(review, candidates(review.projectId))
    }

    /** Flushes marks, then checks the target is alive before the review is marked sent; a dead target reopens the chooser with fresh candidates. */
    private suspend fun deliver(review: Review, ccmSessionId: String?) {
        flush()
        if (ccmSessionId != null && !isLive(ccmSessionId)) {
            offerTargets(review)
            return
        }
        val feedback = io { service.prepareFeedback(review.id) }
        if (ccmSessionId != null) enqueue(ccmSessionId, feedback.prompt)
        else startSession(review.projectId, feedback.prompt, { reportError("$START_FAILED. Feedback is in ${feedback.markdown}") }, { })
    }

    fun approve(review: Review) {
        scope.launch { runGuarded(reportError) { io { service.approve(review.id) } } }
    }

    fun delete(review: Review) {
        scope.launch { runGuarded(reportError) { io { service.delete(review.id) } } }
    }
}
