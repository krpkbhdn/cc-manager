package com.github.krpkbhdn.ccmanager.ui.extensions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.ExtensionFailure
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginJob
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.failureOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs one blocking mutation at a time off the UI thread; [job] says what runs, [failure] what went wrong. */
class MutationRunner(private val scope: CoroutineScope) {
    var job by mutableStateOf<PluginJob?>(null)
        private set
    var failure by mutableStateOf<ExtensionFailure?>(null)

    /** Bumped when a mutation starts and when its work is done, so a list reload that began earlier is dropped. */
    var epoch = 0
        private set

    fun <T> run(label: String, key: String, work: () -> T, onSuccess: (T) -> Unit = {}) {
        if (job != null) return
        job = PluginJob(label, key)
        epoch++
        failure = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                epoch++
                onSuccess(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                failure = failureOf(label, e)
            } finally {
                job = null
            }
        }
    }
}
