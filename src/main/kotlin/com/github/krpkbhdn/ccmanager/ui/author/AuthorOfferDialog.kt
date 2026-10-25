package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val OFFER_HINT = "Install sets up the checked parts and remembers that you declined the unchecked ones. Later asks again next time."

/** Asks whether to install the author's setup; Esc and Later record nothing. */
@Composable
fun AuthorOfferDialog(offer: AuthorOffer, state: AuthorProfileState) {
    AuthorDialog("Set up Claude the author's way", state::closeOffer) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            offer.entries.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppCheckbox(offer.checked[entry.component()] == true, { offer.checked[entry.component()] = it }, enabled = !state.busy)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Label(entry.title(), style = Type.bodyMedium)
                            Chip(versionTag(entry), colors.accent)
                        }
                        Label(entry.summary(), color = colors.textSecondary, style = Type.small, maxLines = 3)
                        ReplaceWarning(entry)
                    }
                }
            }
        }
        Label(OFFER_HINT, color = colors.textTertiary, style = Type.caption, maxLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            AppButton("Later", state::closeOffer, enabled = !state.busy)
            AppButton("Decline", state::declineAll, enabled = !state.busy)
            AppButton("Install", { state.install(offer.chosen(), offer.unchosen()) }, variant = ButtonVariant.PRIMARY, loading = state.busy)
        }
    }
}
