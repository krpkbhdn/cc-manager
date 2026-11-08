package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dotSize = 7.dp
private val dismissSize = 20.dp

/** Blinking "compact?" marker for a session whose context is nearly full; [blockedReason] disables Compact while the session cannot take a command. */
@Composable
internal fun CompactAlert(percentText: String, blockedReason: String?, onCompact: () -> Unit, onDismiss: () -> Unit) {
    val blink = rememberInfiniteTransition(label = "compactAlert").animateFloat(
        initialValue = 1f,
        targetValue = Alpha.PULSE_LOW,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE, easing = LinearEasing), RepeatMode.Reverse),
        label = "compactAlertAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.graphicsLayer { alpha = blink.value }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(dotSize).background(colors.danger, RoundedCornerShape(Radius.pill)))
            Label("Context $percentText - compact?", color = colors.danger, style = Type.caption)
        }
        AppButton("Compact", onCompact, Modifier.height(dismissSize), icon = Icons.Zap, enabled = blockedReason == null, compact = true, tooltip = blockedReason ?: "Asks to type /compact into this session")
        IconButton(Icons.Close, onDismiss, Modifier.size(dismissSize), tooltip = "Dismiss until the context fills up again")
    }
}
