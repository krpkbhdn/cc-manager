package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val trackWidth = 32.dp
private val trackHeight = 18.dp
private val knobSize = 14.dp
private val knobInset = 2.dp

/** On/off toggle; [enabled] false keeps showing the state but ignores clicks. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) colors.accent else colors.borderStrong, Motion.fast(), label = "switchTrack")
    val knob by animateDpAsState(if (checked) trackWidth - knobSize - knobInset else knobInset, Motion.fast(), label = "switchKnob")
    Pressable({ onCheckedChange(!checked) }, modifier.alpha(if (enabled) 1f else Alpha.DISABLED), CircleShape, track, enabled = enabled, role = Role.Switch) {
        Box(Modifier.size(trackWidth, trackHeight)) {
            Box(Modifier.offset(knob, knobInset).size(knobSize).clip(CircleShape).background(if (checked) colors.onAccent else Color.White))
        }
    }
}
