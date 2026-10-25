package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.update.UpdateService
import java.awt.Desktop
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UPDATE_FAILED_TITLE = "Update failed"
private const val UNKNOWN_ERROR = "Something went wrong"

/** Compose state of the self-update flow: periodic check, download on request, restart into the installer. */
class UpdateState(
    private val updates: UpdateService?,
    private val scope: CoroutineScope,
    private val reportError: (title: String, message: String) -> Unit,
    private val requestQuit: (quit: () -> Unit) -> Unit,
    private val exitApp: () -> Unit,
) {
    var status by mutableStateOf<UpdateStatus>(UpdateStatus.None)
        private set
    var dismissed by mutableStateOf(false)
        private set

    val canInstall: Boolean get() = updates?.canInstall() == true

    fun start(): Job? {
        val service = updates?.takeIf { it.enabled() } ?: return null
        return scope.launch {
            while (true) {
                check(service)
                delay(service.checkInterval().toMillis())
            }
        }
    }

    fun dismiss() {
        dismissed = true
    }

    fun download() {
        val service = updates ?: return
        val offered = (status as? UpdateStatus.Available)?.update ?: return
        status = UpdateStatus.Downloading(offered, null)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    service.download(offered) { fraction -> scope.launch { status = UpdateStatus.Downloading(offered, fraction.toFloat()) } }
                }
                status = UpdateStatus.Ready(offered, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                status = UpdateStatus.Available(offered)
                reportError(UPDATE_FAILED_TITLE, e.message ?: UNKNOWN_ERROR)
            }
        }
    }

    fun restart() {
        val ready = status as? UpdateStatus.Ready ?: return
        requestQuit {
            try {
                updates?.installAfterExit(ready.installer)
                exitApp()
            } catch (e: IllegalStateException) {
                reportError(UPDATE_FAILED_TITLE, e.message ?: UNKNOWN_ERROR)
            }
        }
    }

    fun openReleasePage() {
        val update = (status as? UpdateStatus.Available)?.update ?: return
        try {
            Desktop.getDesktop().browse(update.releasePageUri())
        } catch (e: IOException) {
            reportError(UPDATE_FAILED_TITLE, e.message ?: UNKNOWN_ERROR)
        } catch (e: UnsupportedOperationException) {
            reportError(UPDATE_FAILED_TITLE, e.message ?: UNKNOWN_ERROR)
        }
    }

    private suspend fun check(service: UpdateService) {
        if (status is UpdateStatus.Downloading || status is UpdateStatus.Ready) return
        val found = withContext(Dispatchers.IO) { service.findUpdate() }.orElse(null) ?: return
        if ((status as? UpdateStatus.Available)?.update != found) {
            status = UpdateStatus.Available(found)
            dismissed = false
        }
    }
}
