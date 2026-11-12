package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import com.github.krpkbhdn.ccmanager.ui.review.MarkSpace
import com.github.krpkbhdn.ccmanager.ui.review.ReviewTool
import com.github.krpkbhdn.ccmanager.ui.review.Viewport
import com.github.krpkbhdn.ccmanager.web.WebMarkShape
import kotlin.math.ceil
import kotlin.math.floor

private const val CROP_PADDING_CSS = 24.0

/**
 * The whole page document as the "image" the review mark gestures work on: image pixels are document CSS px, the view is the
 * canvas (px) with the page's zoom, placement and scroll. Marks are stored in document px, so inner scroll containers and
 * position:fixed content drift away from their marks when scrolled.
 */
class WebMarkSpace(
    private val layout: () -> PageViewport?,
    private val scroll: () -> DocScroll?,
    private val density: () -> Float,
    private val onPan: (dxDp: Double, dyDp: Double) -> Unit,
    private val onZoom: (cursorDp: Offset, notches: Int) -> Unit,
) : MarkSpace {
    override val imageW: Float get() = scroll()?.w?.toFloat() ?: 0f
    override val imageH: Float get() = scroll()?.h?.toFloat() ?: 0f

    override val viewport: Viewport?
        get() {
            val page = layout() ?: return null
            val doc = scroll()?.takeIf { it.w > 0 && it.h > 0 } ?: return null
            val pxPerCss = (page.scale * density()).toFloat()
            return Viewport(pxPerCss, (page.offsetX * density() - doc.x * pxPerCss).toFloat(), (page.offsetY * density() - doc.y * pxPerCss).toFloat())
        }

    /** The content follows the pointer, so the page pan moves the other way; [dx], [dy] in px. */
    override fun panBy(dx: Float, dy: Float) = onPan(-dx / density().toDouble(), -dy / density().toDouble())

    override fun zoomAt(anchorX: Float, anchorY: Float, factor: Float) =
        onZoom(Offset(anchorX / density(), anchorY / density()), if (factor > 1f) 1 else -1)
}

/** [comment] is carried along for the painter; the document size turns document px into the 0..1 of a mark. */
internal fun WebMarkShape.toMark(docW: Double, docH: Double, comment: String = ""): MarkDraft =
    MarkDraft(kind, x / docW, y / docH, w / docW, h / docH, comment, points.map { MarkPoint(it.x / docW, it.y / docH) })

/** The shape in document px clamped to the document, or null when it is not a valid shape (a polygon with too many points). */
internal fun MarkDraft.toShape(docW: Double, docH: Double): WebMarkShape? = runCatching {
    val left = (x * docW).coerceIn(0.0, docW)
    val top = (y * docH).coerceIn(0.0, docH)
    WebMarkShape(
        kind, left, top, (w * docW).coerceIn(0.0, docW - left), (h * docH).coerceIn(0.0, docH - top),
        points.map { MarkPoint((it.x * docW).coerceIn(0.0, docW), (it.y * docH).coerceIn(0.0, docH)) },
    )
}.getOrNull()

internal fun MarkKind.tool(): ReviewTool = when (this) {
    MarkKind.RECTANGLE -> ReviewTool.RECTANGLE
    MarkKind.ELLIPSE -> ReviewTool.ELLIPSE
    MarkKind.PIN -> ReviewTool.PIN
    MarkKind.FREEFORM -> ReviewTool.FREEFORM
}

/** The viewport rectangle (whole CSS px) around [shape] with a margin, clamped to the viewport; null when the shape is not on screen. */
internal fun cropAround(shape: WebMarkShape, scroll: DocScroll, viewW: Double, viewH: Double): Rect? {
    val left = floor((shape.x - scroll.x - CROP_PADDING_CSS).coerceAtLeast(0.0))
    val top = floor((shape.y - scroll.y - CROP_PADDING_CSS).coerceAtLeast(0.0))
    val right = ceil((shape.x + shape.w - scroll.x + CROP_PADDING_CSS).coerceAtMost(viewW))
    val bottom = ceil((shape.y + shape.h - scroll.y + CROP_PADDING_CSS).coerceAtMost(viewH))
    return if (right - left < 1 || bottom - top < 1) null else Rect(left, top, right - left, bottom - top)
}

/** [shape] in the pixels of a screenshot of the document rectangle [cropDoc] taken at [pixelsPerCss]. */
internal fun WebMarkShape.inCrop(cropDoc: Rect, pixelsPerCss: Double): WebMarkShape = WebMarkShape(
    kind, (x - cropDoc.x) * pixelsPerCss, (y - cropDoc.y) * pixelsPerCss, w * pixelsPerCss, h * pixelsPerCss,
    points.map { MarkPoint((it.x - cropDoc.x) * pixelsPerCss, (it.y - cropDoc.y) * pixelsPerCss) },
)

/** Absolute scroll that puts the middle of [shape] in the middle of [layout]; used right after a load, when the reported scroll is still the old page's. */
internal fun jumpToShape(shape: WebMarkShape, layout: PageViewport): String =
    InspectorBridge.scrollTo(shape.x + shape.w / 2 - layout.widthCss / 2.0, shape.y + shape.h / 2 - layout.heightCss / 2.0)

/** The scroll change that puts the middle of [shape] in the middle of the viewport. */
internal fun scrollDeltaTo(shape: WebMarkShape, scroll: DocScroll, viewW: Double, viewH: Double): Pair<Double, Double> =
    Pair(shape.x + shape.w / 2 - scroll.x - viewW / 2, shape.y + shape.h / 2 - scroll.y - viewH / 2)
