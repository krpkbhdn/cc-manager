package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.background
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.github.krpkbhdn.ccmanager.ui.ReviewPreviewData
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat

private const val FRAME_NANOS = 16_000_000L
private const val SETTLE_FRAMES = 30
private const val POLL_MILLIS = 20L
private const val WAIT_MILLIS = 8_000L
private const val SEEDED = 1L
private const val EMPTY_PROJECT = 2L
private const val FIRST_X = 648f
private const val FIRST_Y = 425f
private const val defaultOut = "C:/Users/Bohdan/Work/Code/Personal/ccm-shots/review"

private class Shot(val name: String, val projectId: Long = SEEDED, val pointer: Offset? = null, val prepare: (ReviewState) -> Unit = {})

private val shots = listOf(
    Shot("review-rect", prepare = { it.selectedMark = 0 }),
    Shot("review-selected-rect", prepare = { it.selectedMark = 0 }),
    Shot("review-freeform", prepare = { it.selectedMark = 3 }),
    Shot("review-freeform-draft", pointer = Offset(FIRST_X, FIRST_Y), prepare = { state ->
        state.arm(ReviewTool.FREEFORM)
        listOf(0.55 to 0.55, 0.75 to 0.55, 0.8 to 0.8, 0.62 to 0.85).forEach { state.draft.add(it.first, it.second, 1000f, 1000f, 0f) }
    }),
    Shot("review-all-marks"),
    Shot("review-empty", EMPTY_PROJECT),
    Shot("review-target-dialog", prepare = { state -> state.send(state.reviews(SEEDED).first()); waitFor { state.sendTarget != null } }),
)

private class PumpedDispatcher : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }

    fun pump() {
        while (true) queue.poll()?.run() ?: return
    }
}

private val loop = PumpedDispatcher()

private fun waitFor(timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    loop.pump()
    while (!condition() && System.currentTimeMillis() < deadline) {
        Thread.sleep(POLL_MILLIS)
        loop.pump()
    }
}

/** Renders the Review screen offscreen over the preview review data. `--out=<dir> --dark|--light --size=WxH`. */
fun main(args: Array<String>) {
    val out = Path.of(args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=") ?: defaultOut)
    val themes = when { "--dark" in args -> listOf(true); "--light" in args -> listOf(false); else -> listOf(true, false) }
    val (w, h) = args.firstOrNull { it.startsWith("--size=") }?.removePrefix("--size=")?.split('x')?.map { it.toInt() } ?: listOf(1280, 820)
    Files.createDirectories(out)
    shots.forEach { shot -> themes.forEach { dark -> render(shot, dark, w, h, out) } }
    kotlin.system.exitProcess(0)
}

private fun render(shot: Shot, dark: Boolean, width: Int, height: Int, out: Path) {
    val service = ReviewPreviewData.service()
    ReviewPreviewData.seed(SEEDED)
    val scope = CoroutineScope(SupervisorJob() + loop)
    val errors = mutableListOf<String>()
    val state = ReviewState(service, { com.github.krpkbhdn.ccmanager.ui.state.LiveSessions() }, { _, _ -> }, { _, _, _, _ -> }, scope, { errors += it })
    state.start()
    waitFor { state.reviews(shot.projectId).isNotEmpty() || shot.projectId == EMPTY_PROJECT }
    if (shot.projectId == SEEDED) {
        val review = state.selectedReview(SEEDED)!!
        waitFor { state.images(review.id).isNotEmpty() }
        val image = state.images(review.id).first()
        waitFor { state.bitmap(image) != null && state.marks(image.id).size == 4 }
    }
    shot.prepare(state)
    val scene = ImageComposeScene(width, height, Density(1f)) { AppTheme(dark) { ReviewScreen(shot.projectId, state, Modifier.background(colors.background)) } }
    try {
        var frame = 0L
        repeat(SETTLE_FRAMES) { loop.pump(); scene.render(frame++ * FRAME_NANOS) }
        shot.pointer?.let { scene.sendPointerEvent(PointerEventType.Move, it) }
        waitFor(300) { false }
        repeat(SETTLE_FRAMES) { loop.pump(); scene.render(frame++ * FRAME_NANOS) }
        val file = out.resolve("${shot.name}-${if (dark) "dark" else "light"}-${width}x$height.png")
        Files.write(file, scene.render(frame * FRAME_NANOS).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("wrote $file${if (errors.isEmpty()) "" else " errors=$errors"}")
    } finally {
        scope.cancel()
        runCatching { scene.close() }
    }
}
