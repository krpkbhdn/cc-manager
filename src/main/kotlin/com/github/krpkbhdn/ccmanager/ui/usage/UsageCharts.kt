package com.github.krpkbhdn.ccmanager.ui.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import kotlin.math.roundToInt
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.git.log.graphColor
import com.github.krpkbhdn.ccmanager.ui.state.formatCost
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.format.DateTimeFormatter

private val chartHeight = 180.dp
private val axisWidth = 52.dp
private val barRowHeight = 10.dp
private val barCorner = 2.dp
private val modelLabelWidth = 160.dp
private val costWidth = 64.dp
private val tooltipGap = 12.dp
private val tooltipDot = 8.dp
private val tooltipLabelMax = 160.dp
private const val HOVER_ALPHA = 0.7f
private const val GRID_LINES = 3
private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

/** Stable colour of a project, the same on every screen and period; "other" is neutral. */
@Composable
fun projectColor(projectId: Long?): Color = if (projectId == null) colors.neutral else graphColor(projectId.toInt(), colors.dark)

/** One stacked column per day, scaled to the most expensive day, with the cost axis on the left and first, middle and last date below. */
@Composable
fun DailyBarsChart(days: List<DayBar>, projectName: (Long) -> String, modifier: Modifier = Modifier) {
    val peak = days.maxOfOrNull { it.totalUsd }?.takeIf { it > 0.0 } ?: 1.0
    val grid = colors.border
    var area by remember { mutableStateOf(IntSize.Zero) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val hit = pointer?.let { dayHit(days, it.x, it.y, area.width.toFloat(), area.height.toFloat(), peak) }
    val palette = days.flatMap { day -> day.slices.map { it.projectId } }.distinct().associateWith { projectColor(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth().height(chartHeight), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.width(axisWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Label(formatCost(peak), color = colors.textTertiary, style = Type.numericCaption)
                Label(formatCost(0.0), color = colors.textTertiary, style = Type.numericCaption)
            }
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { area = it }.pointerInput(Unit) { trackPointer { pointer = it } }) {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(GRID_LINES) { line ->
                        val y = size.height * line / (GRID_LINES - 1)
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    val barWidth = barWidth(size.width, days.size)
                    days.forEachIndexed { index, day ->
                        val left = barLeft(index, size.width, days.size)
                        var bottom = size.height
                        day.slices.forEachIndexed { sliceIndex, slice ->
                            val height = barHeight(slice.costUsd, peak, size.height)
                            bottom -= height
                            val color = palette.getValue(slice.projectId)
                            val lit = hit?.day == index && hit.segment == sliceIndex
                            drawRoundRect(if (lit) color.copy(alpha = HOVER_ALPHA) else color, Offset(left, bottom), Size(barWidth, height), CornerRadius(barCorner.toPx()))
                        }
                    }
                }
                val anchor = pointer
                if (anchor != null && hit != null) ChartTooltip(anchor, dayTooltip(days[hit.day], projectName))
            }
        }
        if (days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = axisWidth + Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(days.first(), days[days.size / 2], days.last()).forEach { Label(it.day.format(dayFormat), color = colors.textTertiary, style = Type.caption) }
            }
        }
    }
}

/** One horizontal bar per model, scaled to the most expensive one; hovering a row shows its exact cost and share. */
@Composable
fun ModelBars(models: List<ModelTotal>, modifier: Modifier = Modifier) {
    val peak = models.maxOfOrNull { it.costUsd }?.takeIf { it > 0.0 } ?: 1.0
    val track = colors.border
    val fill = colors.accent
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var hovered by remember { mutableStateOf<Int?>(null) }
    Box(modifier.pointerInput(Unit) { trackPointer { pointer = it } }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            models.forEachIndexed { index, model ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Label(model.model, Modifier.width(modelLabelWidth), style = Type.small, tooltipWhenTruncated = true)
                    Canvas(
                        Modifier.weight(1f).height(barRowHeight).pointerInput(index, peak) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val position = event.changes.firstOrNull()?.position
                                    val over = event.type != PointerEventType.Exit && position != null &&
                                        modelBarHit(position.x, position.y, size.width.toFloat(), size.height.toFloat(), model.costUsd, peak)
                                    if (over) hovered = index else if (hovered == index) hovered = null
                                }
                            }
                        },
                    ) {
                        val radius = CornerRadius(barCorner.toPx())
                        drawRoundRect(track, size = size, cornerRadius = radius)
                        val barFill = if (hovered == index) fill.copy(alpha = HOVER_ALPHA) else fill
                        drawRoundRect(barFill, size = Size((size.width * model.costUsd / peak).toFloat(), size.height), cornerRadius = radius)
                    }
                    Label(formatCost(model.costUsd), Modifier.width(costWidth), color = colors.textSecondary, style = Type.numeric)
                }
            }
        }
        val anchor = pointer
        val model = hovered?.let(models::getOrNull)
        if (anchor != null && model != null) ChartTooltip(anchor, modelTooltip(model, models))
    }
}

/** Reports the pointer position inside the receiver, null when it leaves. */
private suspend fun PointerInputScope.trackPointer(onPointer: (Offset?) -> Unit) = awaitPointerEventScope {
    while (true) {
        val event = awaitPointerEvent()
        onPointer(if (event.type == PointerEventType.Exit) null else event.changes.firstOrNull()?.position)
    }
}

/** Popup block following the pointer, kept inside the parent bounds. */
@Composable
private fun BoxScope.ChartTooltip(anchor: Offset, content: TooltipContent) {
    val gap = with(LocalDensity.current) { tooltipGap.toPx() }
    Box(Modifier.matchParentSize()) { TooltipBlock(anchor, gap, content) }
}

@Composable
private fun TooltipBlock(anchor: Offset, gap: Float, content: TooltipContent) {
    Column(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                val (x, y) = tooltipOrigin(anchor.x, anchor.y, placeable.width.toFloat(), placeable.height.toFloat(), constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), gap)
                placeable.place(x.roundToInt(), y.roundToInt(), zIndex = 1f)
            }
        }.background(colors.surfaceRaised, RoundedCornerShape(Radius.sm)).border(1.dp, colors.borderStrong, RoundedCornerShape(Radius.sm)).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Label(content.title, style = Type.bodyMedium)
        content.rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (row.swatch) Box(Modifier.size(tooltipDot).background(projectColor(row.projectId), CircleShape))
                Label(row.label, Modifier.widthIn(max = tooltipLabelMax), color = colors.textSecondary, style = Type.small)
                Label(row.value, color = colors.textPrimary, style = Type.numericCaption)
            }
        }
    }
}
