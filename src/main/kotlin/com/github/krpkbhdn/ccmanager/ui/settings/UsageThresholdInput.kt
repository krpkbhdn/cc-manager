package com.github.krpkbhdn.ccmanager.ui.settings

/** Result of reading one threshold text field: the number to save or the message to show under the field. */
sealed interface ThresholdInput<out T> {
    data class Valid<T>(val value: T) : ThresholdInput<T>
    data class Invalid(val message: String) : ThresholdInput<Nothing>
}

/** Pure validation of the usage threshold fields; 0 means the alert is off. */
object UsageThresholdInput {
    const val MAX_PERCENT = 100

    fun percent(text: String): ThresholdInput<Int> {
        val value = text.trim().toIntOrNull()
        return if (value != null && value in 0..MAX_PERCENT) ThresholdInput.Valid(value)
        else ThresholdInput.Invalid("Enter a whole number from 0 to $MAX_PERCENT")
    }

    fun budget(text: String): ThresholdInput<Double> {
        val value = text.trim().replace(',', '.').toDoubleOrNull()
        return if (value != null && value.isFinite() && value >= 0) ThresholdInput.Valid(value)
        else ThresholdInput.Invalid("Enter an amount of 0 or more")
    }
}
