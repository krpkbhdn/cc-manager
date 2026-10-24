package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val trackPadding = 3.dp
private val segmentHeight = 26.dp

/** One choice of a [SegmentedControl]; [count] shows as a small badge when positive. */
data class Segment<T>(val value: T, val label: String, val icon: ImageVector? = null, val count: Int = 0)

/** Compact pill-in-a-track selector, used for the Git tab's sections. */
@Composable
fun <T> SegmentedControl(segments: List<Segment<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val track = RoundedCornerShape(Radius.md)
    Row(
        modifier.background(colors.background, track).border(1.dp, colors.border, track).padding(trackPadding),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { SegmentPill(it, it.value == selected) { onSelect(it.value) } }
    }
}

@Composable
private fun <T> SegmentPill(segment: Segment<T>, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.md - trackPadding)
    val tint by animateColorAsState(if (selected) colors.textPrimary else colors.textSecondary, Motion.fast(), label = "segmentTint")
    val fill by animateColorAsState(if (selected) colors.surfaceRaised else Color.Transparent, Motion.fast(), label = "segmentFill")
    Pressable(onClick, Modifier.height(segmentHeight).then(if (selected) Modifier.border(1.dp, colors.borderStrong, shape) else Modifier), shape, base = fill) {
        Row(
            Modifier.padding(horizontal = Spacing.md - 2.dp).height(segmentHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        ) {
            segment.icon?.let { Glyph(it, tint, size = IconSize.medium) }
            Label(segment.label, color = tint, style = if (selected) Type.smallMedium else Type.small)
            if (segment.count > 0) CountBadge(segment.count, tint = if (selected) colors.accent else colors.textTertiary)
        }
    }
}
