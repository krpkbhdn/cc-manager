package com.github.krpkbhdn.ccmanager.ui.settings

/** Pure rules of the percent stepper: what a typed text or an arrow press turns the current value into. */
object PercentStep {
    fun digitsOnly(text: String): String = text.filter { it in '0'..'9' }

    /** The clamped number typed in [text], or [current] when it holds no number. */
    fun commit(text: String, current: Int, min: Int, max: Int): Int =
        digitsOnly(text).toIntOrNull()?.coerceIn(min, max) ?: current

    fun step(current: Int, delta: Int, min: Int, max: Int): Int = (current + delta).coerceIn(min, max)
}
