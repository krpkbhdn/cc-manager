package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val defaultBox = 16.dp
private val hitPadding = 6.dp

/** Accent-filled checkbox; [indeterminate] shows a dash for "some of its children". */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false,
    enabled: Boolean = true,
    boxSize: Dp = defaultBox,
) {
    val hitSize = boxSize + hitPadding
    val glyphSize = boxSize - 4.dp
    val on = checked || indeterminate
    val fill by animateColorAsState(if (on) colors.accent else colors.surface, Motion.fast(), label = "checkFill")
    val edge by animateColorAsState(if (on) colors.accent else colors.borderStrong, Motion.fast(), label = "checkEdge")
    val shape = RoundedCornerShape(Radius.xs)
    Pressable({ onChange(!checked) }, modifier.size(hitSize).alpha(if (enabled) 1f else Alpha.DISABLED), RoundedCornerShape(Radius.sm), enabled = enabled, role = Role.Checkbox) {
        Box(Modifier.align(Alignment.Center).size(boxSize).background(fill, shape).border(1.dp, edge, shape), contentAlignment = Alignment.Center) {
            if (on) Glyph(if (indeterminate && !checked) Icons.Minus else Icons.Check, colors.onAccent, size = glyphSize)
        }
    }
}
