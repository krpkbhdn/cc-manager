package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val fieldWidth = 84.dp

/** Percent field between a minus and a plus arrow; typing commits on Enter or focus loss, so half-typed values are never applied. */
@Composable
fun PercentStepper(value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val text = remember(value) { TextFieldState(value.toString()) }
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.collect { typed ->
            val digits = PercentStep.digitsOnly(typed)
            if (digits != typed) text.edit { replace(0, length, digits) }
        }
    }
    fun commit() {
        val committed = PercentStep.commit(text.text.toString(), value, min, max)
        text.edit { replace(0, length, committed.toString()) }
        if (committed != value) onChange(committed)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconButton(Icons.ChevronLeft, { onChange(PercentStep.step(value, -step, min, max)) }, tooltip = "Decrease", enabled = value > min)
        AppTextField(
            text,
            Modifier.width(fieldWidth).onFocusChanged { if (!it.isFocused) commit() },
            onSubmit = ::commit,
            trailing = { Label("%", color = colors.textSecondary, style = Type.body) },
        )
        IconButton(Icons.ChevronRight, { onChange(PercentStep.step(value, step, min, max)) }, tooltip = "Increase", enabled = value < max)
    }
}
