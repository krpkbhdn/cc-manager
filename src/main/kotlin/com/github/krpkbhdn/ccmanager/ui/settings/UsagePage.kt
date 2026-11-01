package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val fieldWidth = 120.dp

@Composable
fun UsagePage(state: AppState) {
    val current = state.prefs.usageThresholds
    SettingGroup("Alerts") {
        Label("The 5-hour and 7-day limits do not apply to Team plans; only the daily budget works there.", color = colors.textSecondary, style = Type.small, maxLines = 2)
        SettingRow("5-hour limit", "Notify when the 5-hour limit reaches this percent. 0 turns it off.") {
            ThresholdField(current.fiveHourPercent().toString(), UsageThresholdInput::percent, state.prefs::setUsageFiveHourPercent)
        }
        SettingRow("7-day limit", "Notify when the 7-day limit reaches this percent. 0 turns it off.") {
            ThresholdField(current.sevenDayPercent().toString(), UsageThresholdInput::percent, state.prefs::setUsageSevenDayPercent)
        }
        SettingRow("Daily budget (USD)", "Notify when the cost of one day reaches this amount. 0 turns it off.") {
            ThresholdField(current.dailyBudgetUsd().toString(), UsageThresholdInput::budget, state.prefs::setUsageDailyBudgetUsd)
        }
    }
}

/** Numeric field that saves a valid text on Enter or when it loses focus; an invalid text stays in the field with its message and is not saved. */
@Composable
private fun <T> ThresholdField(initial: String, parse: (String) -> ThresholdInput<T>, onValid: (T) -> Unit) {
    val text = remember { TextFieldState(initial) }
    val commit = {
        val typed = text.text.toString()
        if (typed != initial) (parse(typed) as? ThresholdInput.Valid)?.let { onValid(it.value) }
        Unit
    }
    Column(Modifier.width(fieldWidth)) {
        AppTextField(text, Modifier.onFocusChanged { if (!it.isFocused) commit() }, mono = true, onSubmit = commit)
        (parse(text.text.toString()) as? ThresholdInput.Invalid)?.let { Label(it.message, color = colors.danger, style = Type.small, maxLines = 3) }
    }
}
