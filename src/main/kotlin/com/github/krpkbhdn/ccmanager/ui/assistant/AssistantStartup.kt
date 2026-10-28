package com.github.krpkbhdn.ccmanager.ui.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MILLIS = 300L

/** What happens to the Assistant when the app starts; saved in the UI settings. */
enum class AssistantStartup(val label: String) {
    ASK("Ask every time"),
    AUTO_START("Start automatically"),
    NEVER("Never"),
    ;

    fun next() = entries[(ordinal + 1) % entries.size]
}

enum class StartupStep { NOTHING, ASK, START }

/** The step at app start: nothing without a usable Claude or when the start was already handled this run. */
fun startupStep(mode: AssistantStartup, claudeReady: Boolean, handled: Boolean): StartupStep = when {
    !claudeReady || handled -> StartupStep.NOTHING
    mode == AssistantStartup.AUTO_START -> StartupStep.START
    mode == AssistantStartup.ASK -> StartupStep.ASK
    else -> StartupStep.NOTHING
}

/** Starts the Assistant at app start or asks first (once per run, after other dialogs are gone); Esc records nothing. */
class AssistantStartupState(
    private val scope: CoroutineScope,
    private val mode: () -> AssistantStartup,
    private val setMode: (AssistantStartup) -> Unit,
    private val blocked: () -> Boolean,
    private val startInBackground: () -> Unit,
    private val start: () -> Unit,
    private val pollMillis: Long = POLL_MILLIS,
) {
    var asking by mutableStateOf(false)
        private set
    var dontAskAgain by mutableStateOf(false)

    private var handled = false

    fun atStart(claudeReady: Boolean) {
        val step = startupStep(mode(), claudeReady, handled)
        handled = true
        when (step) {
            StartupStep.START -> startInBackground()
            StartupStep.ASK -> scope.launch {
                while (blocked()) delay(pollMillis)
                asking = true
            }
            StartupStep.NOTHING -> Unit
        }
    }

    fun choose(startNow: Boolean) {
        asking = false
        if (dontAskAgain) setMode(if (startNow) AssistantStartup.AUTO_START else AssistantStartup.NEVER)
        if (startNow) start()
        dontAskAgain = false
    }

    fun dismiss() {
        asking = false
        dontAskAgain = false
    }
}
