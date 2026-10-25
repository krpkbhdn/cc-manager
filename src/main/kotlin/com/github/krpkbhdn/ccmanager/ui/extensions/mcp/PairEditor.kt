package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** One key and value being typed; the value is a secret. */
class PairRow(val key: TextFieldState = TextFieldState(), val value: TextFieldState = TextFieldState())

/** Rows of key and secret value with a shared show/hide toggle for the values. */
@Composable
fun PairEditor(title: String, addLabel: String, rows: SnapshotStateList<PairRow>, modifier: Modifier = Modifier) {
    var revealed by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(title, Modifier.weight(1f), style = Type.smallMedium)
            if (rows.isNotEmpty()) IconButton(if (revealed) Icons.EyeOff else Icons.Eye, { revealed = !revealed }, tooltip = if (revealed) "Hide values" else "Show values", active = revealed)
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppTextField(row.key, Modifier.weight(1f), placeholder = "Name", mono = true)
                AppTextField(row.value, Modifier.weight(1.4f), placeholder = "Value", mono = true, masked = !revealed)
                IconButton(Icons.Close, { rows.remove(row) }, tooltip = "Remove")
            }
        }
        if (rows.isEmpty()) Label("Values are treated as secrets and are only sent to claude.", color = colors.textTertiary, style = Type.caption)
        AppButton(addLabel, { rows.add(PairRow()) }, variant = ButtonVariant.GHOST, icon = Icons.Plus, compact = true)
    }
}
