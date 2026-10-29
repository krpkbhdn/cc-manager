package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNEXPECTED_FAILURE_TITLE = "Voice download failed"

internal const val UNKNOWN_ERROR = "Something went wrong"

/**
 * Downloads one chain of assets at a time and keeps the status of those in flight or failed. A finished download is not kept here: [reload]
 * re-reads what is installed first, then the entry goes. A failure shows in its row; only an unexpected one also goes to [reportError].
 */
internal class ResourceDownloads(
    private val assets: VoiceAssetService,
    private val scope: CoroutineScope,
    private val reportError: (title: String, message: String) -> Unit,
    private val reload: suspend () -> Unit,
) {
    private val statuses = mutableStateMapOf<VoiceAsset, VoiceResourceStatus>()
    private val running = AtomicBoolean()

    var active by mutableStateOf(false)
        private set

    fun statusOf(asset: VoiceAsset): VoiceResourceStatus? = statuses[asset]

    fun forgetFailedIn(installed: Set<VoiceAsset>) {
        statuses.entries.toList().filter { it.value is VoiceResourceStatus.Failed && it.key in installed }.forEach { statuses.remove(it.key) }
    }

    fun start(targets: List<VoiceAsset>) {
        if (targets.isEmpty() || !running.compareAndSet(false, true)) return
        active = true
        scope.launch {
            try {
                for (asset in targets) {
                    if (!downloadOne(asset)) break
                }
            } finally {
                active = false
                running.set(false)
            }
            reload()
        }
    }

    private suspend fun downloadOne(asset: VoiceAsset): Boolean {
        statuses[asset] = VoiceResourceStatus.Downloading(0f)
        try {
            withContext(Dispatchers.IO) { assets.install(asset) { fraction -> statuses[asset] = VoiceResourceStatus.Downloading(fraction.toFloat()) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            statuses[asset] = VoiceResourceStatus.Failed(e.message ?: UNKNOWN_ERROR)
            return false
        } catch (e: RuntimeException) {
            val message = e.message ?: UNKNOWN_ERROR
            statuses[asset] = VoiceResourceStatus.Failed(message)
            reportError(UNEXPECTED_FAILURE_TITLE, message)
            return false
        }
        reload()
        statuses.remove(asset)
        return true
    }
}
