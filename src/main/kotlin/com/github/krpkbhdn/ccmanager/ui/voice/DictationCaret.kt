package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

private const val ATTACK_MILLIS = 25
private const val DECAY_MILLIS = 140
private const val COLOUR_FADE_MILLIS = 200
private const val HUE_CYCLE_MILLIS = 2500
private const val DEGREES = 360f
private const val SPEECH_LEVEL = 0.12f
private const val OVERSHOOT_SHARE = 0.06f

/** How far the bar stays from the preceding character, in widths of one space. */
internal const val CARET_GAP_IN_SPACES = 0.75f
private const val SPEAKING_ALPHA = 1f
private const val SATURATION = 0.75f
private const val BRIGHTNESS = 0.95f
private val barWidth = 9.dp
private val restHeight = 2.dp

/**
 * The volume bar: a flat "_" in silence, [width] wide and [restHeight] tall with its bottom a share of the line height below the line and [gap] right of the caret.
 * Only its height changes with [level], upwards, to reach the same distance above the line at full level.
 */
internal fun caretBar(cursor: Rect, level: Float, width: Float, restHeight: Float, gap: Float): Rect {
    val overshoot = cursor.height * OVERSHOOT_SHARE
    val bottom = cursor.bottom + overshoot
    val fullHeight = cursor.height + 2 * overshoot
    val height = restHeight + (fullHeight - restHeight) * level.coerceIn(0f, 1f)
    val left = cursor.left + gap
    return Rect(left, bottom - height, left + width, bottom)
}

/** 1 while the level counts as speech, 0 in silence: what the caret colour fades towards. */
internal fun speechTarget(level: Float): Float = if (level >= SPEECH_LEVEL) 1f else 0f

/** One colour for the whole caret: [grey] in silence, the rainbow colour at [hue] (0 to 1 round the spectrum) when fully [speaking], smoothly mixed between. */
internal fun caretColor(grey: Color, hue: Float, speaking: Float): Color =
    lerp(grey, Color.hsv((hue * DEGREES) % DEGREES, SATURATION, BRIGHTNESS, SPEAKING_ALPHA), speaking.coerceIn(0f, 1f))

/** What the caret needs per frame: the eased level, how much it is "speaking" (0 grey to 1 rainbow) and the position on the hue circle. */
internal class CaretMotion(val level: State<Float>, val speaking: State<Float>, val hue: State<Float>)

@Composable
internal fun rememberCaretMotion(level: Float): CaretMotion {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(level) { shown.animateTo(level, tween(if (level > shown.value) ATTACK_MILLIS else DECAY_MILLIS)) }
    val speaking = animateFloatAsState(speechTarget(level), tween(COLOUR_FADE_MILLIS), label = "caretSpeaking")
    val hue = rememberInfiniteTransition(label = "caretHue").animateFloat(0f, 1f, infiniteRepeatable(tween(HUE_CYCLE_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = "hue")
    return remember { CaretMotion(shown.asState(), speaking, hue) }
}

/**
 * Draws [caretBar] at the selection of [state] over a field whose own caret is hidden; [gap] is the distance kept from the preceding character. [layout] is the field's latest text layout and [scroll]
 * its scroll (vertical; horizontal for a single-line field), since the layout rectangles are in unscrolled text coordinates.
 */
internal fun Modifier.dictationCaret(state: TextFieldState, layout: () -> TextLayoutResult?, scroll: ScrollState, motion: CaretMotion, grey: Color, gap: Float, horizontal: Boolean = false) =
    drawWithContent {
        drawContent()
        val result = layout() ?: return@drawWithContent
        val offset = state.selection.min.coerceIn(0, result.layoutInput.text.length)
        val bar = caretBar(result.getCursorRect(offset), motion.level.value, barWidth.toPx(), restHeight.toPx(), gap).translate(if (horizontal) -scroll.value.toFloat() else 0f, if (horizontal) 0f else -scroll.value.toFloat())
        drawRect(caretColor(grey, motion.hue.value, motion.speaking.value), bar.topLeft, bar.size)
    }
