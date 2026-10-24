package com.github.krpkbhdn.ccmanager.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/** Animation durations (ms) and easing shared by every transition. */
object Motion {
    const val FAST = 120
    const val BASE = 180
    const val SLOW = 240
    const val PULSE = 1600
    const val SHIMMER = 1400
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast() = tween<T>(FAST, easing = ease)
    fun <T> base() = tween<T>(BASE, easing = ease)
    fun <T> slow() = tween<T>(SLOW, easing = ease)
}
