package com.github.krpkbhdn.ccmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.state.UpdateState
import com.github.krpkbhdn.ccmanager.ui.state.UpdateStatus
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private const val PERCENT = 100

/** One line saying where the self-update stands; the buttons beside it come from [UpdateState]. */
internal fun updateSummary(update: UpdateState): String = when (val status = update.status) {
    is UpdateStatus.Available -> "Version ${status.update.version} is available."
    is UpdateStatus.Downloading -> "Downloading version ${status.update.version}${status.fraction?.let { " ${(it * PERCENT).toInt()}%" }.orEmpty()}"
    is UpdateStatus.Ready -> "Version ${status.update.version} is ready. Restart to apply it."
    UpdateStatus.None -> when {
        !update.enabled -> "Update checks are off in a run from sources."
        update.checking -> "Checking for updates..."
        update.checkFailed -> "Could not check for updates."
        update.upToDate -> "You have the latest version."
        else -> "Checked automatically every few hours."
    }
}

@Composable
fun UpdatesPage(update: UpdateState) {
    SettingGroup("Version") {
        SettingRow("Current version", update.currentVersion.ifBlank { "Not a release build" }) {}
        SettingRow("Status", updateSummary(update)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { UpdateButtons(update) }
        }
    }
}

@Composable
private fun UpdateButtons(update: UpdateState) {
    when (update.status) {
        is UpdateStatus.Available -> {
            AppButton("Release notes", update::openReleasePage)
            if (update.canInstall) AppButton("Download", update::download, variant = ButtonVariant.PRIMARY)
        }
        is UpdateStatus.Downloading -> AppButton("Downloading", {}, variant = ButtonVariant.PRIMARY, enabled = false, loading = true)
        is UpdateStatus.Ready -> AppButton("Restart", update::restart, variant = ButtonVariant.PRIMARY)
        UpdateStatus.None -> AppButton(if (update.checkFailed) "Retry" else "Check for updates", update::checkNow, enabled = update.enabled && !update.checking, loading = update.checking)
    }
}
