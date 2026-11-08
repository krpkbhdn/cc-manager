package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val WIDTH_SHARE = 0.92f
private const val HEIGHT_SHARE = 0.86f
private val maxDialogWidth = 1040.dp
private val maxDialogHeight = 720.dp
private val areaPaneWidth = 250.dp
private val middlePaneWidth = 200.dp
private val searchWidth = 240.dp
private val iconBox = 36.dp

/** The author's configuration catalog: areas on the left, groups in the middle, item cards on the right; Esc and Not now record nothing. */
@Composable
fun AuthorCatalogDialog(view: CatalogView, state: AuthorProfileState) {
    BoxWithConstraints {
        ModalHost(state::closeCatalog, width = minOf(maxDialogWidth, maxWidth * WIDTH_SHARE), height = minOf(maxDialogHeight, maxHeight * HEIGHT_SHARE)) {
            Column(Modifier.fillMaxSize()) {
                Header(view, if (view.offer) "Set up Claude the author's way" else "Author configuration")
                HorizontalRule()
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    AuthorAreaPane(view, Modifier.width(areaPaneWidth))
                    VerticalRule()
                    AuthorGroupPane(view, !state.busy, Modifier.width(middlePaneWidth))
                    VerticalRule()
                    Box(Modifier.weight(1f).fillMaxHeight()) { AuthorItemList(view, !state.busy) }
                }
                HorizontalRule()
                Footer(view, state)
            }
        }
    }
}

@Composable
private fun Header(view: CatalogView, title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.size(iconBox).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
            Glyph(Icons.Sparkles, colors.accent, size = 20.dp)
        }
        Label(title, Modifier.weight(1f), style = Type.subtitle)
        AppTextField(view.query, Modifier.width(searchWidth), placeholder = "Search all items", icon = Icons.Search)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppSwitch(view.onlyNew, { view.onlyNew = it })
            Label("Only new and updated", color = colors.textSecondary, style = Type.small)
        }
    }
}

@Composable
private fun Footer(view: CatalogView, state: AuthorProfileState) {
    val count = view.selected.size
    val declinable = CatalogSelection.toDecline(view.entries, view.selected).size
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("$count selected", Modifier.weight(1f), color = colors.textSecondary, style = Type.bodyMedium)
        AppButton("Not now", state::closeCatalog, enabled = !state.busy)
        if (view.offer) AppButton("Don't offer these", state::declineUnchecked, enabled = declinable > 0 && !state.busy)
        AppButton(if (count > 0) "Install $count selected" else "Install", state::install, variant = ButtonVariant.PRIMARY, enabled = count > 0, loading = state.busy)
    }
}
