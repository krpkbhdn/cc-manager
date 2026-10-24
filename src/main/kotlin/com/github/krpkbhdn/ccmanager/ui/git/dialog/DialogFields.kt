package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val radioSize = 16.dp
private val radioDot = 8.dp

/** Small caption above a form control; [error] replaces the caption in red. */
@Composable
fun LabeledField(label: String, error: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(error ?: label, color = if (error != null) colors.danger else colors.textSecondary, style = Type.smallMedium)
        content()
    }
}

@Composable
fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppCheckbox(checked, onChange)
        Column {
            Label(label, color = colors.textPrimary)
            if (hint != null) Label(hint, color = colors.textTertiary, style = Type.caption, maxLines = 2)
        }
    }
}

/** A choice with its explanation, for pick-one lists such as merge or reset modes. */
data class Choice<T>(val value: T, val label: String, val description: String)

@Composable
fun <T> ChoiceList(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        choices.forEach { choice ->
            val on = choice.value == selected
            Pressable({ onSelect(choice.value) }, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm), base = if (on) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent) {
                Row(Modifier.padding(Spacing.sm + 2.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Radio(on)
                    Column {
                        Label(choice.label, color = colors.textPrimary, style = Type.bodyMedium)
                        Label(choice.description, color = colors.textSecondary, style = Type.caption, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun Radio(on: Boolean) {
    val ring = if (on) colors.accent else colors.borderStrong
    Box(Modifier.padding(top = 2.dp).size(radioSize).border(1.5.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(radioDot).background(colors.accent, CircleShape))
    }
}
