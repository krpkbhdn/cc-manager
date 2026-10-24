package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.ui.component.Text

object FontSize {
    val caption = 11.sp
    val small = 12.sp
    val body = 13.sp
    val heading = 15.sp
    val title = 22.sp
}

private val iconActionSize = 26.dp
private val dividerThickness = 1.dp

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = colors.textPrimary,
    size: TextUnit = FontSize.body,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    mono: Boolean = false,
    align: TextAlign = TextAlign.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        textAlign = align,
        fontFamily = if (mono) FontFamily.Monospace else null,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Hoverable, clickable rounded surface; [content] learns whether the pointer is over it. */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.sm),
    base: Color = Color.Transparent,
    hover: Color = colors.surfaceHover,
    enabled: Boolean = true,
    content: @Composable BoxScope.(hovered: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (hovered && enabled) hover else base)
            .hoverable(source)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick),
    ) { content(hovered && enabled) }
}

@Composable
fun IconAction(icon: AppIcon, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = colors.textSecondary, enabled: Boolean = true) {
    Pressable(onClick, modifier.size(iconActionSize).alpha(if (enabled) 1f else Alpha.DISABLED), enabled = enabled) {
        Glyph(icon, tint, Modifier.align(Alignment.Center))
    }
}

@Composable
fun Modifier.cardSurface(shape: Shape = RoundedCornerShape(Radius.md)): Modifier =
    clip(shape).background(colors.surface).border(dividerThickness, colors.border, shape)

@Composable
fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(dividerThickness).background(colors.border))
}

@Composable
fun VerticalRule(modifier: Modifier = Modifier) {
    Box(modifier.width(dividerThickness).fillMaxHeight().background(colors.border))
}

@Composable
fun Pill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(Radius.pill)).background(tint.copy(alpha = Alpha.SOFT))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) { Label(text, color = tint, size = FontSize.caption, weight = FontWeight.Medium) }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(title.uppercase(), color = colors.textTertiary, size = FontSize.caption, weight = FontWeight.SemiBold)
        count?.let {
            Spacer(Modifier.width(Spacing.sm))
            Label(it.toString(), color = colors.textTertiary, size = FontSize.caption)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ColorBadge(count: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Box(modifier.size(size).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Label(count.coerceAtMost(MAX_BADGE).toString(), color = Color.White, size = 10.sp, weight = FontWeight.Bold)
    }
}

private const val MAX_BADGE = 9
