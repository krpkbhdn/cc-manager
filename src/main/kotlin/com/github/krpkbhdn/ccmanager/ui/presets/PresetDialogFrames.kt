package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 640.dp
private val maxDialogHeight = 620.dp
private const val HEIGHT_SHARE = 0.86f
private val iconBox = 40.dp

/** Height of the single-line fields in the presets UI, a little roomier than the app default. */
internal val inputHeight = 40.dp

/**
 * The frame of the add and edit dialogs: a 640dp card up to 620dp high whose form scrolls, one primary [saveLabel] button and a line
 * for the error the save reports. [onSave] gets the callback that shows that error.
 */
@Composable
fun ComponentDialogFrame(
    title: String,
    icon: ImageVector,
    saveLabel: String,
    canSave: Boolean,
    onSave: (onError: (String) -> Unit) -> Unit,
    onClose: () -> Unit,
    hasInput: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints {
        ModalHost(onClose, width = dialogWidth, hasInput = hasInput) {
            Box(Modifier.heightIn(max = minOf(maxDialogHeight, maxHeight * HEIGHT_SHARE))) {
                DialogBody(title, icon, colors.accent, actions = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        error?.let { Label(it, color = colors.danger, style = Type.small, maxLines = 3) }
                        DialogActions(saveLabel, canSave, false, { error = null; onSave { error = it } }, onClose)
                    }
                }, content = content)
            }
        }
    }
}

/** A fixed-height card (up to 620dp) for dialogs with their own scrolling list: header, then [content] filling the rest. */
@Composable
fun PickerDialogFrame(title: String, icon: ImageVector, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints {
        ModalHost(onClose, width = dialogWidth, height = minOf(maxDialogHeight, maxHeight * HEIGHT_SHARE)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(iconBox).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                        Glyph(icon, colors.accent, size = IconSize.xlarge)
                    }
                    Label(title, style = Type.subtitle)
                }
                HorizontalRule()
                content()
            }
        }
    }
}
