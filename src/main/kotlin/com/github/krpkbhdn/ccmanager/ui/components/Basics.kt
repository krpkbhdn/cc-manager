package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val hairline = 1.dp
private const val FULL_TURN = 360f
private const val SPINNER_SWEEP = 100f
private const val SPINNER_MILLIS = 800
private const val SHIMMER_SPAN = 600f
private const val SKELETON_SPREAD = 0.5f

/** Single-line text by default, truncated with an ellipsis; set [tooltipWhenTruncated] to reveal the full text on hover. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = colors.textPrimary,
    style: TextStyle = Type.body,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Unspecified,
    tooltipWhenTruncated: Boolean = false,
) {
    var truncated by remember(text) { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        BasicText(
            text = text,
            modifier = modifier,
            style = style.copy(color = color, textAlign = align),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow },
        )
    }
    if (tooltipWhenTruncated) WithTooltip(text.takeIf { truncated }, content = content) else content()
}

@Composable
fun Glyph(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
fun HorizontalRule(modifier: Modifier = Modifier, color: Color = colors.border) {
    Box(modifier.fillMaxWidth().height(hairline).background(color))
}

@Composable
fun VerticalRule(modifier: Modifier = Modifier, color: Color = colors.border) {
    Box(modifier.width(hairline).fillMaxHeight().background(color))
}

/** Pill-shaped tag tinted with [tint]; [leading] sits before the text. */
@Composable
fun Chip(text: String, tint: Color, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(tint.copy(alpha = Alpha.SOFT)).padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        leading?.invoke()
        Label(text, color = tint, style = Type.captionMedium)
    }
}

/** Neutral count badge, e.g. the number of live sessions. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, tint: Color = colors.textSecondary) {
    Box(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(tint.copy(alpha = Alpha.SOFT)).padding(horizontal = Spacing.sm, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) { Label(count.toString(), color = tint, style = Type.numericCaption) }
}

/** Keycap hint such as "Ctrl" or "K". */
@Composable
fun Kbd(key: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier.clip(shape).background(colors.hoverOverlay).border(hairline, colors.border, shape).padding(horizontal = Spacing.xs + 1.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) { Label(key, color = colors.textSecondary, style = Type.captionMedium) }
}

@Composable
fun Shortcut(vararg keys: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs + 1.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { Kbd(it) }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = colors.accent) {
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPINNER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension / SPINNER_STROKE_DIVISOR, cap = StrokeCap.Round)
        drawArc(tint.copy(alpha = Alpha.SOFT), 0f, FULL_TURN, false, style = stroke)
        drawArc(tint, angle, SPINNER_SWEEP, false, style = stroke)
    }
}

private const val SPINNER_STROKE_DIVISOR = 8f

/** Placeholder block with a travelling highlight, for content that is still loading. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(Radius.sm)) {
    val travel by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -SHIMMER_SPAN,
        targetValue = SHIMMER_SPAN * 2,
        animationSpec = infiniteRepeatable(tween(Motion.SHIMMER, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerTravel",
    )
    val base = colors.hoverOverlay
    val glint = colors.pressOverlay
    Box(
        modifier.clip(shape).background(base).background(
            Brush.linearGradient(
                listOf(Color.Transparent, glint, Color.Transparent),
                start = Offset(travel, 0f),
                end = Offset(travel + SHIMMER_SPAN * SKELETON_SPREAD, SHIMMER_SPAN * SKELETON_SPREAD),
            ),
        ),
    )
}
