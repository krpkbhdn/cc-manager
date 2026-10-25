package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.Banner
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.UpdateState
import com.github.krpkbhdn.ccmanager.ui.state.UpdateStatus
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val PERCENT = 100

/** Strip above the content: offers a new release, shows the download and asks for the restart that applies it. */
@Composable
fun UpdateBanner(update: UpdateState) {
    when (val status = update.status) {
        UpdateStatus.None -> Unit
        is UpdateStatus.Available -> if (!update.dismissed) {
            Banner("Version ${status.update.version} is available", colors.accent, Icons.Download) {
                GhostButton("Later", update::dismiss)
                if (update.canInstall) PrimaryButton("Download", update::download) else PrimaryButton("Open release page", update::openReleasePage)
            }
        }
        is UpdateStatus.Downloading -> {
            val percent = status.fraction?.let { " ${(it * PERCENT).toInt()}%" }.orEmpty()
            Banner("Downloading version ${status.update.version}$percent", colors.accent, Icons.Download) {
                PrimaryButton("Downloading", {}, enabled = false, loading = true)
            }
        }
        is UpdateStatus.Ready -> Banner("Version ${status.update.version} is ready. Restart to apply the update", colors.success, Icons.Refresh) {
            PrimaryButton("Restart", update::restart)
        }
    }
}
