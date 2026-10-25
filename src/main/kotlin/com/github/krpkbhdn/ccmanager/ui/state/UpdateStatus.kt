package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.update.AvailableUpdate
import java.nio.file.Path

/** Where the self-update stands: nothing new, offered, being downloaded, or downloaded and waiting for a restart. */
sealed interface UpdateStatus {
    data object None : UpdateStatus
    data class Available(val update: AvailableUpdate) : UpdateStatus
    data class Downloading(val update: AvailableUpdate, val fraction: Float?) : UpdateStatus
    data class Ready(val update: AvailableUpdate, val installer: Path) : UpdateStatus
}
