package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.ui.component.Text

private val emptyIconBox = 56.dp
private val emptyTextWidth = 460.dp
private val menuWidth = 200.dp

@Composable
fun EmptyState(
    icon: AppIcon,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(Spacing.xl).widthIn(max = emptyTextWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(emptyIconBox).clip(CircleShape).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(icon, colors.accent, size = 26.dp)
            }
            Label(title, size = FontSize.heading, weight = FontWeight.SemiBold)
            Text(message, color = colors.textSecondary, fontSize = FontSize.body, textAlign = TextAlign.Center)
            action()
        }
    }
}

/** Slim tinted strip with an icon and message; [trailing] holds optional actions. */
@Composable
fun Banner(message: String, tint: Color, icon: AppIcon, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(tint.copy(alpha = Alpha.SOFT)).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Glyph(icon, tint)
        Label(message, Modifier.weight(1f), color = colors.textPrimary, size = FontSize.small)
        trailing()
    }
}

data class MenuEntry(val label: String, val onSelect: () -> Unit)

private const val POPUP_GAP_PX = 6

private class BelowAnchor(private val alignEnd: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val left = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        return IntOffset(left, anchorBounds.bottom + POPUP_GAP_PX)
    }
}

/** Dropdown menu anchored below its parent composable. */
@Composable
fun PopupMenu(entries: List<MenuEntry>, onDismiss: () -> Unit, alignEnd: Boolean = false) {
    Popup(popupPositionProvider = remember(alignEnd) { BelowAnchor(alignEnd) }, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val shape = RoundedCornerShape(Radius.md)
        Column(Modifier.widthIn(min = menuWidth).cardSurface(shape).padding(Spacing.xs)) {
            entries.forEach { entry ->
                Pressable({ onDismiss(); entry.onSelect() }, Modifier.fillMaxWidth()) {
                    Label(entry.label, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
                }
            }
        }
    }
}
