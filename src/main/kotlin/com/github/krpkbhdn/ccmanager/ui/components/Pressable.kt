package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val focusRingWidth = 2.dp
private const val FOCUS_RING_ALPHA = 0.7f

/** Tracks whether the last input came from the keyboard, so focus rings appear for Tab but not for mouse clicks. */
object InputModality {
    var keyboard by mutableStateOf(false)
}

fun Modifier.trackInputModality(): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) InputModality.keyboard = true
        false
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) InputModality.keyboard = false
            }
        }
    }

/**
 * Clickable rounded surface with animated hover/press feedback, hand cursor and a keyboard focus ring.
 * [hover] replaces [base] while hovered; without it a translucent overlay is laid over [base].
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.sm),
    base: Color = Color.Transparent,
    hover: Color? = null,
    enabled: Boolean = true,
    pressScale: Float = 1f,
    role: Role = Role.Button,
    content: @Composable BoxScope.(hovered: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val active = hovered && enabled
    val surface by animateColorAsState(if (active && hover != null) hover else base, Motion.fast(), label = "pressSurface")
    val overlay by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            pressed -> colors.pressOverlay
            active && hover == null -> colors.hoverOverlay
            else -> Color.Transparent
        },
        Motion.fast(),
        label = "pressOverlay",
    )
    val scale by animateFloatAsState(if (pressed && enabled) pressScale else 1f, Motion.fast(), label = "pressScale")
    val ring = if (focused && InputModality.keyboard) Modifier.border(focusRingWidth, colors.accent.copy(alpha = FOCUS_RING_ALPHA), shape) else Modifier
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(surface)
            .background(overlay)
            .hoverable(source)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, interactionSource = source, indication = null, role = role, onClick = onClick)
            .then(ring),
    ) { content(active) }
}

/** True when [key] is the key pressed to confirm in text fields and lists. */
fun isConfirmKey(key: Key): Boolean = key == Key.Enter || key == Key.NumPadEnter
