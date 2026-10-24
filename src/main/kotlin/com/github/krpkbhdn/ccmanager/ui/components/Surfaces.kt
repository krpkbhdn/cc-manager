package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val hairline = 1.dp
private val emptyIconBox = 52.dp
private val emptyTextWidth = 420.dp
private val menuWidth = 208.dp
private val menuElevation = 16.dp
private val cardElevation = 10.dp
private const val POPUP_GAP_PX = 6
private const val POP_START_SCALE = 0.96f

/** Layered surface: fill, gradient hairline (brighter at the top edge) and, when [raised], a soft shadow. */
@Composable
fun Modifier.card(shape: Shape = RoundedCornerShape(Radius.lg), color: Color = colors.surface, raised: Boolean = false): Modifier =
    (if (raised) shadow(cardElevation, shape, ambientColor = colors.shadow, spotColor = colors.shadow) else this)
        .clip(shape)
        .background(color)
        .border(hairline, Brush.verticalGradient(listOf(colors.borderStrong, colors.border)), shape)

/** Fades and scales a floating surface in from [origin]; call it on the outermost modifier of a popup or dialog. */
@Composable
fun Modifier.popIn(origin: TransformOrigin = TransformOrigin.Center): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, Motion.base(), label = "popIn")
    return graphicsLayer {
        alpha = progress
        val scale = POP_START_SCALE + (1f - POP_START_SCALE) * progress
        scaleX = scale
        scaleY = scale
        transformOrigin = origin
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: Color = colors.accent,
    action: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Spacing.xl).widthIn(max = emptyTextWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.size(emptyIconBox).clip(RoundedCornerShape(Radius.lg)).background(tint.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(icon, tint, size = 24.dp)
            }
            Label(title, style = Type.subtitle)
            Label(message, color = colors.textSecondary, maxLines = 5, align = TextAlign.Center)
            action()
        }
    }
}

/** Slim tinted strip with an icon and message; [trailing] holds optional actions. */
@Composable
fun Banner(message: String, tint: Color, icon: ImageVector, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(tint.copy(alpha = Alpha.SOFT)).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Glyph(icon, tint)
        Label(message, Modifier.weight(1f), color = colors.textPrimary, style = Type.small)
        trailing()
    }
}

data class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val hint: String? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val dividerAbove: Boolean = false,
    val onSelect: () -> Unit,
)

private class BelowAnchor(private val alignEnd: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val left = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val fitted = left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + POPUP_GAP_PX
        val top = if (below + popupContentSize.height > windowSize.height) anchorBounds.top - popupContentSize.height - POPUP_GAP_PX else below
        return IntOffset(fitted, top.coerceAtLeast(0))
    }
}

/** Dropdown menu anchored to its parent composable; Esc or an outside click dismisses it. */
@Composable
fun PopupMenu(entries: List<MenuEntry>, onDismiss: () -> Unit, alignEnd: Boolean = false) {
    AnchoredPopup(onDismiss, alignEnd, Modifier.widthIn(min = menuWidth).width(IntrinsicSize.Max)) {
        Column(Modifier.padding(Spacing.xs)) { entries.forEach { MenuItem(it, onDismiss) } }
    }
}

/** Raised floating card anchored below its parent composable; [modifier] sizes it, Esc or an outside click dismisses it. */
@Composable
fun AnchoredPopup(onDismiss: () -> Unit, alignEnd: Boolean = false, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Popup(popupPositionProvider = remember(alignEnd) { BelowAnchor(alignEnd) }, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val shape = RoundedCornerShape(Radius.md)
        Box(
            Modifier.popIn(TransformOrigin(if (alignEnd) 1f else 0f, 0f))
                .shadow(menuElevation, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .then(modifier).card(shape, colors.surfaceRaised),
        ) { content() }
    }
}

@Composable
private fun MenuItem(entry: MenuEntry, onDismiss: () -> Unit) {
    val tint = if (entry.danger) colors.danger else colors.textPrimary
    if (entry.dividerAbove) HorizontalRule(Modifier.padding(vertical = Spacing.xs))
    Pressable({ onDismiss(); entry.onSelect() }, Modifier.fillMaxWidth().alpha(if (entry.enabled) 1f else Alpha.DISABLED), shape = RoundedCornerShape(Radius.sm), enabled = entry.enabled) {
        Row(Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.sm - 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md - 2.dp)) {
            entry.icon?.let { Glyph(it, if (entry.danger) colors.danger else colors.textSecondary) }
            Label(entry.label, Modifier.weight(1f), color = tint, style = Type.body)
            entry.hint?.let { Label(it, color = colors.textTertiary, style = Type.monoSmall) }
        }
    }
}
