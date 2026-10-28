package com.github.krpkbhdn.ccmanager.ui.sessions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Slowly pulsing clock and [text] that flags a working session gone silent. */
@Composable
fun StaleMarker(text: String, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "staleMarker").animateFloat(
        initialValue = 1f,
        targetValue = Alpha.PULSE_LOW,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE, easing = LinearEasing), RepeatMode.Reverse),
        label = "staleAlpha",
    )
    Row(modifier.alpha(pulse.value), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Glyph(Icons.Clock, colors.warning, size = Spacing.md)
        Label(text, color = colors.warning, style = Type.numeric)
    }
}
