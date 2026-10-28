package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val segmentHeight = 28.dp
private val chevronWidth = 24.dp

/** Compact secondary button with a chevron segment opening [menu], or runs [onChevron] instead when given; [label] may be null for an icon-only main segment. */
@Composable
fun SplitActionButton(
    label: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    menu: List<MenuEntry> = emptyList(),
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    tooltip: String? = null,
    onChevron: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val left = RoundedCornerShape(topStart = Radius.md, bottomStart = Radius.md)
    val right = RoundedCornerShape(topEnd = Radius.md, bottomEnd = Radius.md)
    Row(modifier.alpha(if (enabled) 1f else Alpha.DISABLED), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            AppButton(label, onClick, icon = icon, enabled = enabled, loading = loading, compact = true, tooltip = tooltip, shape = left)
        } else {
            WithTooltip(tooltip) {
                Pressable(onClick, Modifier.height(segmentHeight).width(segmentHeight + 2.dp).border(1.dp, colors.borderStrong, left), left, base = colors.surfaceRaised, hover = colors.surfaceHover, enabled = enabled && !loading) {
                    if (loading) Spinner(Modifier.align(Alignment.Center), 14.dp) else Glyph(icon, colors.textPrimary, Modifier.align(Alignment.Center), size = IconSize.large)
                }
            }
        }
        Box(Modifier.width(1.dp).height(segmentHeight).background(colors.borderStrong))
        Box {
            Pressable(
                { onChevron?.invoke() ?: run { expanded = true } },
                Modifier.height(segmentHeight).width(chevronWidth).border(1.dp, colors.borderStrong, right),
                right,
                base = colors.surfaceRaised,
                hover = colors.surfaceHover,
                enabled = enabled && !loading,
            ) { Glyph(Icons.ChevronDown, colors.textSecondary, Modifier.align(Alignment.Center), size = IconSize.medium) }
            if (expanded) PopupMenu(menu, onDismiss = { expanded = false }, alignEnd = true)
        }
    }
}
