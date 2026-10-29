package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.voice.DictationRequest
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.VoiceInputService
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val COMMITTED_WINDOW = 1024
private const val CLOSE_TIMEOUT_MILLIS = 4000L

/**
 * One live dictation test at a time. Every state change happens under [lock]; a run that was stopped or failed never changes the state again and
 * its session is closed whenever it turns up. Starts are serialized, so a start never overlaps a start that is still loading.
 */
internal class LiveTestController(private val voice: VoiceInputService, private val scope: CoroutineScope) {
    var state by mutableStateOf<LiveTestState>(LiveTestState.Idle)
        private set

    /** The tail of what was heard so far, see [LiveTranscriptTail]. */
    var text by mutableStateOf("")
        private set

    val active: Boolean get() = state == LiveTestState.Loading || state == LiveTestState.Listening

    private val lock = Any()
    private val startOrder = Mutex()
    private var run = LiveRun()

    fun start(request: DictationRequest) {
        val current = synchronized(lock) {
            if (active) return
            text = ""
            state = LiveTestState.Loading
            LiveRun().also { run = it }
        }
        scope.launch { begin(current, request) }
    }

    fun stop() {
        val (current, session) = synchronized(lock) {
            if (!active || run.stopped) return
            run.stopped = true
            if (run.session == null) state = LiveTestState.Stopped
            run to run.session
        }
        if (session != null) scope.launch { closeAndFinish(current, session) }
    }

    /** Stops the test and waits (bounded) until the microphone is released; for the app exit, when no coroutine would still run. */
    fun close() {
        val session = synchronized(lock) {
            if (active) {
                run.stopped = true
                state = LiveTestState.Stopped
            }
            run.session
        }
        if (session != null) thread(isDaemon = true) { session.close() }.join(CLOSE_TIMEOUT_MILLIS)
    }

    private suspend fun begin(current: LiveRun, request: DictationRequest) {
        try {
            startOrder.withLock {
                if (current.stopped) return
                // The session is adopted inside the non-cancellable block: a result handed back to a cancelled coroutine would be lost.
                withContext(Dispatchers.IO + NonCancellable) { adopt(current, voice.start(request, listenerOf(current))) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            fail(current, e.message ?: UNKNOWN_ERROR)
        }
    }

    private fun adopt(current: LiveRun, session: DictationSession) {
        val kept = synchronized(lock) {
            if (!current.stopped) {
                current.session = session
                state = LiveTestState.Listening
            }
            !current.stopped
        }
        if (!kept) session.close()
    }

    private suspend fun closeAndFinish(current: LiveRun, session: DictationSession) {
        closeSession(session)
        synchronized(lock) { if (run === current) state = LiveTestState.Stopped }
    }

    private suspend fun closeSession(session: DictationSession) = withContext(Dispatchers.IO + NonCancellable) { session.close() }

    private fun fail(current: LiveRun, message: String): DictationSession? = synchronized(lock) {
        if (current.stopped) return null
        current.stopped = true
        state = LiveTestState.Error(message)
        current.session
    }

    private fun listenerOf(current: LiveRun) = object : DictationListener {
        override fun onPartial(text: String) = hear(current) { current.partial = text }

        override fun onFinal(text: String) = hear(current) {
            current.committed = (current.committed + " " + text).takeLast(COMMITTED_WINDOW)
            current.partial = ""
        }

        override fun onError(error: Throwable) {
            val session = fail(current, error.message ?: error.toString()) ?: return
            scope.launch { closeSession(session) }
        }
    }

    private fun hear(current: LiveRun, change: () -> Unit) = synchronized(lock) {
        change()
        if (run === current) text = LiveTranscriptTail.of(current.committed + " " + current.partial)
    }

    private class LiveRun {
        var stopped = false
        var session: DictationSession? = null
        var committed = ""
        var partial = ""
    }
}
